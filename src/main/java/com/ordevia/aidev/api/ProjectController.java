package com.ordevia.aidev.api;

import com.ordevia.aidev.project.application.ProjectDagPlanner;
import com.ordevia.aidev.project.application.ProjectService;
import com.ordevia.aidev.project.application.ProjectWaveExecutor;
import com.ordevia.aidev.project.domain.Project;
import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.domain.ProjectRepositoryKind;
import com.ordevia.aidev.project.domain.WaveExecution;
import com.ordevia.aidev.project.domain.WaveExecutionItem;
import com.ordevia.aidev.project.infrastructure.ProjectJpaRepository;
import com.ordevia.aidev.project.infrastructure.WaveExecutionItemJpaRepository;
import com.ordevia.aidev.project.infrastructure.WaveExecutionJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemRepositoryBinding;
import com.ordevia.aidev.workitem.domain.WorkItemRepositoryPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectJpaRepository projects;
    private final ProjectService projectService;
    private final ProjectDagPlanner planner;
    private final ProjectWaveExecutor executor;
    private final WaveExecutionJpaRepository waveExecutions;
    private final WaveExecutionItemJpaRepository waveExecutionItems;

    public ProjectController(ProjectJpaRepository projects,
                             ProjectService projectService,
                             ProjectDagPlanner planner,
                             ProjectWaveExecutor executor,
                             WaveExecutionJpaRepository waveExecutions,
                             WaveExecutionItemJpaRepository waveExecutionItems) {
        this.projects = projects;
        this.projectService = projectService;
        this.planner = planner;
        this.executor = executor;
        this.waveExecutions = waveExecutions;
        this.waveExecutionItems = waveExecutionItems;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(request.name(), request.description(), request.repositoryPath());
    }

    @GetMapping public List<Project> list() { return projects.findAll(); }

    @GetMapping("/{id}")
    public Project get(@PathVariable UUID id) {
        return projects.findById(id).orElseThrow(() -> new NoSuchElementException("Project not found"));
    }

    @GetMapping("/{id}/repositories")
    public List<ProjectRepository> repositories(@PathVariable UUID id) {
        return projectService.listRepositories(id);
    }

    @PostMapping("/{id}/repositories")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectRepository addRepository(@PathVariable UUID id, @Valid @RequestBody CreateRepositoryRequest request) {
        return projectService.addRepository(id, new ProjectService.RepositoryProfile(
                request.alias(), request.kind(), request.repositoryPath(), request.baseBranch(), request.branchPrefix(),
                request.instructionsPath(), request.buildCommand(), request.testCommand(), request.javaVersion(), request.nodeVersion()));
    }

    @GetMapping("/{id}/work-items")
    public List<WorkItem> workItems(@PathVariable UUID id) { return projectService.listWorkItems(id); }

    @PostMapping("/{id}/work-items")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItem addWorkItem(@PathVariable UUID id, @Valid @RequestBody CreateProjectWorkItemRequest request) {
        List<ProjectService.RepositoryBindingRequest> bindings = request.repositories() == null ? List.of() : request.repositories().stream()
                .map(binding -> new ProjectService.RepositoryBindingRequest(binding.alias(), binding.purpose(), binding.baseBranchOverride()))
                .toList();
        return projectService.addWorkItem(id, request.externalId(), request.title(), request.description(), request.blockedBy(), bindings);
    }

    @GetMapping("/{projectId}/work-items/{workItemId}/repositories")
    public List<ProjectService.RepositoryBindingView> workItemRepositories(@PathVariable UUID projectId, @PathVariable UUID workItemId) {
        return projectService.listBindings(projectId, workItemId);
    }

    @PostMapping("/{projectId}/work-items/{workItemId}/repositories")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItemRepositoryBinding bindRepository(@PathVariable UUID projectId,
                                                     @PathVariable UUID workItemId,
                                                     @Valid @RequestBody RepositoryBindingRequest request) {
        return projectService.bindRepository(projectId, workItemId,
                new ProjectService.RepositoryBindingRequest(request.alias(), request.purpose(), request.baseBranchOverride()));
    }

    @PostMapping("/{projectId}/work-items/{workItemId}/dependencies/{blockerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addDependency(@PathVariable UUID projectId, @PathVariable UUID workItemId, @PathVariable UUID blockerId) {
        projectService.addDependency(projectId, workItemId, blockerId);
    }

    @GetMapping("/{id}/dag")
    public ProjectDagPlanner.DagPlan dag(@PathVariable UUID id) { return planner.plan(id); }

    @PostMapping("/{id}/execute-ready")
    public ProjectWaveExecutor.WaveRunResult executeReady(@PathVariable UUID id) { return executor.executeReady(id); }

    @GetMapping("/{id}/wave-executions")
    public List<WaveExecution> waveExecutions(@PathVariable UUID id) {
        if (!projects.existsById(id)) throw new NoSuchElementException("Project not found");
        return waveExecutions.findByProjectIdOrderByStartedAtDesc(id);
    }

    @GetMapping("/{projectId}/wave-executions/{waveId}/items")
    public List<WaveExecutionItem> waveExecutionItems(@PathVariable UUID projectId, @PathVariable UUID waveId) {
        WaveExecution wave = waveExecutions.findById(waveId).orElseThrow(() -> new NoSuchElementException("Wave execution not found"));
        if (!projectId.equals(wave.getProjectId())) throw new IllegalArgumentException("Wave execution does not belong to project");
        return waveExecutionItems.findByWaveExecutionIdOrderByStartedAtAsc(waveId);
    }

    public record CreateProjectRequest(@NotBlank String name, String description, @NotBlank String repositoryPath) {}

    public record CreateRepositoryRequest(
            @NotBlank String alias,
            @NotNull ProjectRepositoryKind kind,
            @NotBlank String repositoryPath,
            String baseBranch,
            String branchPrefix,
            String instructionsPath,
            String buildCommand,
            String testCommand,
            String javaVersion,
            String nodeVersion
    ) {}

    public record RepositoryBindingRequest(@NotBlank String alias, @NotNull WorkItemRepositoryPurpose purpose, String baseBranchOverride) {}

    public record CreateProjectWorkItemRequest(
            String externalId,
            @NotBlank String title,
            String description,
            List<UUID> blockedBy,
            List<RepositoryBindingRequest> repositories
    ) {}
}
