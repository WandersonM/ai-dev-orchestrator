package com.ordevia.aidev.project.application;

import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemDependency;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemDependencyJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectDagPlanner {
    private final WorkItemJpaRepository workItems;
    private final WorkItemDependencyJpaRepository dependencies;

    public ProjectDagPlanner(WorkItemJpaRepository workItems, WorkItemDependencyJpaRepository dependencies) {
        this.workItems = workItems;
        this.dependencies = dependencies;
    }

    public DagPlan plan(UUID projectId) {
        List<WorkItem> items = workItems.findByProjectIdOrderByCreatedAtAsc(projectId);
        if (items.isEmpty()) return new DagPlan(List.of(), List.of(), List.of());

        Map<UUID, WorkItem> byId = items.stream().collect(Collectors.toMap(WorkItem::getId, Function.identity()));
        List<WorkItemDependency> edges = dependencies.findByWorkItemIdIn(new ArrayList<>(byId.keySet()));

        Map<UUID, Set<UUID>> blockedBy = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> dependents = new LinkedHashMap<>();
        byId.keySet().forEach(id -> {
            blockedBy.put(id, new LinkedHashSet<>());
            dependents.put(id, new LinkedHashSet<>());
        });

        for (WorkItemDependency edge : edges) {
            if (!byId.containsKey(edge.getBlockedByWorkItemId())) {
                throw new IllegalStateException("Dependency points outside project: " + edge.getBlockedByWorkItemId());
            }
            blockedBy.get(edge.getWorkItemId()).add(edge.getBlockedByWorkItemId());
            dependents.get(edge.getBlockedByWorkItemId()).add(edge.getWorkItemId());
        }

        Map<UUID, Integer> indegree = new LinkedHashMap<>();
        blockedBy.forEach((id, deps) -> indegree.put(id, deps.size()));
        ArrayDeque<UUID> ready = new ArrayDeque<>();
        indegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });

        List<Wave> waves = new ArrayList<>();
        Set<UUID> visited = new LinkedHashSet<>();
        int waveNumber = 1;
        while (!ready.isEmpty()) {
            int size = ready.size();
            List<WorkItemView> waveItems = new ArrayList<>();
            List<UUID> current = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                UUID id = ready.remove();
                current.add(id);
                visited.add(id);
                waveItems.add(view(byId.get(id), blockedBy.get(id)));
            }
            waves.add(new Wave(waveNumber++, waveItems));
            for (UUID id : current) {
                for (UUID dependent : dependents.get(id)) {
                    int next = indegree.computeIfPresent(dependent, (ignored, degree) -> degree - 1);
                    if (next == 0) ready.add(dependent);
                }
            }
        }

        List<WorkItemView> cyclic = byId.keySet().stream()
                .filter(id -> !visited.contains(id))
                .map(id -> view(byId.get(id), blockedBy.get(id)))
                .toList();

        List<WorkItemView> executableNow = items.stream()
                .filter(this::isWorkflowExecutable)
                .filter(item -> blockedBy.get(item.getId()).stream()
                        .map(byId::get)
                        .allMatch(blocker -> blocker.getStatus() == WorkItemStatus.DONE))
                .map(item -> view(item, blockedBy.get(item.getId())))
                .toList();

        return new DagPlan(waves, cyclic, executableNow);
    }

    private boolean isWorkflowExecutable(WorkItem item) {
        return switch (item.getStatus()) {
            case NEW,
                 READY_FOR_DOMAIN_VALIDATION,
                 READY_FOR_ARCHITECTURE,
                 READY_FOR_DEVELOPMENT,
                 INTEGRATING,
                 QA_VALIDATING,
                 REVIEWING,
                 SECURITY_REVIEWING,
                 RELEASE_PREPARING,
                 CHANGES_REQUESTED -> true;
            default -> false;
        };
    }

    private WorkItemView view(WorkItem item, Set<UUID> blockedBy) {
        return new WorkItemView(item.getId(), item.getExternalId(), item.getTitle(), item.getStatus(), List.copyOf(blockedBy));
    }

    public record DagPlan(List<Wave> waves, List<WorkItemView> cyclicItems, List<WorkItemView> executableNow) {
        public boolean valid() { return cyclicItems.isEmpty(); }
    }
    public record Wave(int number, List<WorkItemView> items) {}
    public record WorkItemView(UUID id, String externalId, String title, WorkItemStatus status, List<UUID> blockedBy) {}
}
