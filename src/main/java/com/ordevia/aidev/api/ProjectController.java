package com.ordevia.aidev.api;

import com.ordevia.aidev.project.application.ProjectDagPlanner;
import com.ordevia.aidev.project.application.ProjectService;
import com.ordevia.aidev.project.application.ProjectWaveExecutor;
import com.ordevia.aidev.project.domain.Project;
import com.ordevia.aidev.project.infrastructure.ProjectJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    public ProjectController(ProjectJpaRepository projects,
                             ProjectService projectService,
                             ProjectDagPlanner planner,
                             ProjectWaveExecutor executor) {
        this.projects = projects;
        this.projectService = projectService;
        this.planner = planner;
        this.executor = executor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(request.name(), request.description(), request.repositoryPath());
    }

    @GetMapping
    public List<Project> list() { return projects.findAll(); }

    @GetMapping("/{id}")
    public Project get(@PathVariable UUID id) {
        return projects.findById(id).orElseThrow(() -> new NoSuchElementException("Project not found"));
    }

    @GetMapping("/{id}/work-items")
    public List<WorkItem> workItems(@PathVariable UUID id) { return projectService.listWorkItems(id); }

    @PostMapping("/{id}/work-items")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItem addWorkItem(@PathVariable UUID id, @Valid @RequestBody CreateProjectWorkItemRequest request) {
        return projectService.addWorkItem(id, request.externalId(), request.title(), request.description(), request.blockedBy());
    }

    @PostMapping("/{projectId}/work-items/{workItemId}/dependencies/{blockerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addDependency(@PathVariable UUID projectId, @PathVariable UUID workItemId, @PathVariable UUID blockerId) {
        projectService.addDependency(projectId, workItemId, blockerId);
    }

    @GetMapping("/{id}/dag")
    public ProjectDagPlanner.DagPlan dag(@PathVariable UUID id) { return planner.plan(id); }

    @PostMapping("/{id}/execute-ready")
    public ProjectWaveExecutor.WaveExecution executeReady(@PathVariable UUID id) { return executor.executeReady(id); }

    public record CreateProjectRequest(@NotBlank String name, String description, @NotBlank String repositoryPath) {}
    public record CreateProjectWorkItemRequest(String externalId, @NotBlank String title, String description, List<UUID> blockedBy) {}
}
