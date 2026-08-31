package com.ordevia.aidev.project.application;

import com.ordevia.aidev.project.domain.Project;
import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.domain.ProjectRepositoryKind;
import com.ordevia.aidev.project.infrastructure.ProjectJpaRepository;
import com.ordevia.aidev.project.infrastructure.ProjectRepositoryJpaRepository;
import com.ordevia.aidev.workitem.domain.*;
import com.ordevia.aidev.workitem.infrastructure.WorkItemDependencyJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemRepositoryBindingJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectJpaRepository projects;
    private final ProjectRepositoryJpaRepository repositories;
    private final WorkItemJpaRepository workItems;
    private final WorkItemRepositoryBindingJpaRepository workItemRepositories;
    private final WorkItemDependencyJpaRepository dependencies;
    private final ProjectDagPlanner planner;

    public ProjectService(ProjectJpaRepository projects,
                          ProjectRepositoryJpaRepository repositories,
                          WorkItemJpaRepository workItems,
                          WorkItemRepositoryBindingJpaRepository workItemRepositories,
                          WorkItemDependencyJpaRepository dependencies,
                          ProjectDagPlanner planner) {
        this.projects = projects;
        this.repositories = repositories;
        this.workItems = workItems;
        this.workItemRepositories = workItemRepositories;
        this.dependencies = dependencies;
        this.planner = planner;
    }

    @Transactional
    public Project create(String name, String description, String repositoryPath) {
        Project project = projects.save(new Project(UUID.randomUUID(), name, description, repositoryPath));
        repositories.save(new ProjectRepository(
                UUID.randomUUID(), project.getId(), "default", ProjectRepositoryKind.OTHER,
                repositoryPath, "main", "ai/", ".ai/AGENTS.md", null, null, null, null));
        return project;
    }

    @Transactional
    public ProjectRepository addRepository(UUID projectId, RepositoryProfile profile) {
        requireProject(projectId);
        if (repositories.existsByProjectIdAndAlias(projectId, profile.alias())) {
            throw new IllegalArgumentException("Repository alias already exists: " + profile.alias());
        }
        ProjectRepository repository = new ProjectRepository(
                UUID.randomUUID(), projectId, profile.alias(), profile.kind(), profile.repositoryPath(),
                profile.baseBranch(), profile.branchPrefix(), profile.instructionsPath(), profile.buildCommand(),
                profile.testCommand(), profile.javaVersion(), profile.nodeVersion());
        return repositories.save(repository);
    }

    @Transactional(readOnly = true)
    public List<ProjectRepository> listRepositories(UUID projectId) {
        requireProject(projectId);
        return repositories.findByProjectIdOrderByAliasAsc(projectId);
    }

    @Transactional
    public WorkItem addWorkItem(UUID projectId, String externalId, String title, String description, List<UUID> blockedBy) {
        return addWorkItem(projectId, externalId, title, description, blockedBy, List.of());
    }

    @Transactional
    public WorkItem addWorkItem(UUID projectId, String externalId, String title, String description,
                                List<UUID> blockedBy, List<RepositoryBindingRequest> repositoryBindings) {
        Project project = requireProject(projectId);
        List<RepositoryBindingRequest> bindings = repositoryBindings == null ? List.of() : repositoryBindings;
        ProjectRepository primaryRepository = resolvePrimaryRepository(projectId, bindings);

        WorkItem item = workItems.save(new WorkItem(
                UUID.randomUUID(), projectId, externalId, title, description, primaryRepository.getRepositoryPath()));

        if (bindings.isEmpty()) {
            workItemRepositories.save(new WorkItemRepositoryBinding(
                    UUID.randomUUID(), item.getId(), primaryRepository.getId(), WorkItemRepositoryPurpose.PRIMARY, null));
        } else {
            for (RepositoryBindingRequest binding : bindings) {
                ProjectRepository repository = requireRepository(projectId, binding.alias());
                workItemRepositories.save(new WorkItemRepositoryBinding(
                        UUID.randomUUID(), item.getId(), repository.getId(), binding.purpose(), binding.baseBranchOverride()));
            }
        }

        for (UUID blockerId : blockedBy == null ? List.<UUID>of() : blockedBy) {
            WorkItem blocker = workItems.findById(blockerId).orElseThrow(() -> new NoSuchElementException("Blocker WorkItem not found: " + blockerId));
            if (!projectId.equals(blocker.getProjectId())) throw new IllegalArgumentException("Dependencies must belong to the same project");
            dependencies.save(new WorkItemDependency(UUID.randomUUID(), item.getId(), blockerId));
        }

        if (!planner.plan(projectId).valid()) throw new IllegalArgumentException("Dependency creates a cycle in the project DAG");
        return item;
    }

    @Transactional
    public WorkItemRepositoryBinding bindRepository(UUID projectId, UUID workItemId, RepositoryBindingRequest request) {
        WorkItem item = requireProjectItem(projectId, workItemId);
        ProjectRepository repository = requireRepository(projectId, request.alias());
        if (workItemRepositories.existsByWorkItemIdAndProjectRepositoryId(item.getId(), repository.getId())) {
            throw new IllegalArgumentException("Repository already bound to WorkItem: " + request.alias());
        }
        return workItemRepositories.save(new WorkItemRepositoryBinding(
                UUID.randomUUID(), item.getId(), repository.getId(), request.purpose(), request.baseBranchOverride()));
    }

    @Transactional(readOnly = true)
    public List<RepositoryBindingView> listBindings(UUID projectId, UUID workItemId) {
        requireProjectItem(projectId, workItemId);
        List<RepositoryBindingView> result = new ArrayList<>();
        for (WorkItemRepositoryBinding binding : workItemRepositories.findByWorkItemIdOrderByCreatedAtAsc(workItemId)) {
            ProjectRepository repository = repositories.findById(binding.getProjectRepositoryId())
                    .orElseThrow(() -> new IllegalStateException("Bound ProjectRepository not found"));
            result.add(new RepositoryBindingView(binding, repository));
        }
        return result;
    }

    @Transactional
    public void addDependency(UUID projectId, UUID workItemId, UUID blockerId) {
        WorkItem item = requireProjectItem(projectId, workItemId);
        WorkItem blocker = requireProjectItem(projectId, blockerId);
        if (dependencies.existsByWorkItemIdAndBlockedByWorkItemId(item.getId(), blocker.getId())) return;
        dependencies.save(new WorkItemDependency(UUID.randomUUID(), item.getId(), blocker.getId()));
        if (!planner.plan(projectId).valid()) throw new IllegalArgumentException("Dependency creates a cycle in the project DAG");
    }

    @Transactional(readOnly = true)
    public List<WorkItem> listWorkItems(UUID projectId) {
        requireProject(projectId);
        return workItems.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    private ProjectRepository resolvePrimaryRepository(UUID projectId, List<RepositoryBindingRequest> bindings) {
        if (!bindings.isEmpty()) {
            RepositoryBindingRequest primary = bindings.stream()
                    .filter(binding -> binding.purpose() == WorkItemRepositoryPurpose.PRIMARY)
                    .findFirst()
                    .orElse(bindings.getFirst());
            return requireRepository(projectId, primary.alias());
        }
        return repositories.findByProjectIdAndAlias(projectId, "default")
                .or(() -> repositories.findFirstByProjectIdAndEnabledTrueOrderByCreatedAtAsc(projectId))
                .orElseThrow(() -> new IllegalStateException("Project has no enabled repository profile"));
    }

    private Project requireProject(UUID projectId) {
        return projects.findById(projectId).orElseThrow(() -> new NoSuchElementException("Project not found"));
    }

    private ProjectRepository requireRepository(UUID projectId, String alias) {
        ProjectRepository repository = repositories.findByProjectIdAndAlias(projectId, alias)
                .orElseThrow(() -> new NoSuchElementException("Project repository not found: " + alias));
        if (!repository.isEnabled()) throw new IllegalStateException("Project repository is disabled: " + alias);
        return repository;
    }

    private WorkItem requireProjectItem(UUID projectId, UUID workItemId) {
        WorkItem item = workItems.findById(workItemId).orElseThrow(() -> new NoSuchElementException("WorkItem not found: " + workItemId));
        if (!projectId.equals(item.getProjectId())) throw new IllegalArgumentException("WorkItem does not belong to project");
        return item;
    }

    public record RepositoryProfile(
            String alias,
            ProjectRepositoryKind kind,
            String repositoryPath,
            String baseBranch,
            String branchPrefix,
            String instructionsPath,
            String buildCommand,
            String testCommand,
            String javaVersion,
            String nodeVersion
    ) {}

    public record RepositoryBindingRequest(String alias, WorkItemRepositoryPurpose purpose, String baseBranchOverride) {}
    public record RepositoryBindingView(WorkItemRepositoryBinding binding, ProjectRepository repository) {}
}
