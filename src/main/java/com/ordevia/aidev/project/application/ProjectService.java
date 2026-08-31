package com.ordevia.aidev.project.application;

import com.ordevia.aidev.project.domain.Project;
import com.ordevia.aidev.project.infrastructure.ProjectJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemDependency;
import com.ordevia.aidev.workitem.infrastructure.WorkItemDependencyJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectJpaRepository projects;
    private final WorkItemJpaRepository workItems;
    private final WorkItemDependencyJpaRepository dependencies;
    private final ProjectDagPlanner planner;

    public ProjectService(ProjectJpaRepository projects,
                          WorkItemJpaRepository workItems,
                          WorkItemDependencyJpaRepository dependencies,
                          ProjectDagPlanner planner) {
        this.projects = projects;
        this.workItems = workItems;
        this.dependencies = dependencies;
        this.planner = planner;
    }

    @Transactional
    public Project create(String name, String description, String repositoryPath) {
        return projects.save(new Project(UUID.randomUUID(), name, description, repositoryPath));
    }

    @Transactional
    public WorkItem addWorkItem(UUID projectId, String externalId, String title, String description, List<UUID> blockedBy) {
        Project project = projects.findById(projectId).orElseThrow(() -> new NoSuchElementException("Project not found"));
        WorkItem item = workItems.save(new WorkItem(UUID.randomUUID(), projectId, externalId, title, description, project.getRepositoryPath()));

        for (UUID blockerId : blockedBy == null ? List.<UUID>of() : blockedBy) {
            WorkItem blocker = workItems.findById(blockerId).orElseThrow(() -> new NoSuchElementException("Blocker WorkItem not found: " + blockerId));
            if (!projectId.equals(blocker.getProjectId())) throw new IllegalArgumentException("Dependencies must belong to the same project");
            dependencies.save(new WorkItemDependency(UUID.randomUUID(), item.getId(), blockerId));
        }

        if (!planner.plan(projectId).valid()) throw new IllegalArgumentException("Dependency creates a cycle in the project DAG");
        return item;
    }

    @Transactional
    public void addDependency(UUID projectId, UUID workItemId, UUID blockerId) {
        WorkItem item = requireProjectItem(projectId, workItemId);
        WorkItem blocker = requireProjectItem(projectId, blockerId);
        if (dependencies.existsByWorkItemIdAndBlockedByWorkItemId(item.getId(), blocker.getId())) return;
        dependencies.save(new WorkItemDependency(UUID.randomUUID(), item.getId(), blocker.getId()));
        if (!planner.plan(projectId).valid()) throw new IllegalArgumentException("Dependency creates a cycle in the project DAG");
    }

    public List<WorkItem> listWorkItems(UUID projectId) {
        if (!projects.existsById(projectId)) throw new NoSuchElementException("Project not found");
        return workItems.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    private WorkItem requireProjectItem(UUID projectId, UUID workItemId) {
        WorkItem item = workItems.findById(workItemId).orElseThrow(() -> new NoSuchElementException("WorkItem not found: " + workItemId));
        if (!projectId.equals(item.getProjectId())) throw new IllegalArgumentException("WorkItem does not belong to project");
        return item;
    }
}
