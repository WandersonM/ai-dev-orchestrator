package com.ordevia.aidev.project.application;

import com.ordevia.aidev.workflow.application.WorkflowEngine;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class ProjectWaveExecutor {
    private final ProjectDagPlanner planner;
    private final WorkflowEngine workflow;
    private final WorkItemJpaRepository workItems;
    private final int maxParallel;

    public ProjectWaveExecutor(ProjectDagPlanner planner,
                               WorkflowEngine workflow,
                               WorkItemJpaRepository workItems,
                               @Value("${aidev.orchestration.max-parallel:3}") int maxParallel) {
        this.planner = planner;
        this.workflow = workflow;
        this.workItems = workItems;
        this.maxParallel = Math.max(1, maxParallel);
    }

    public WaveExecution executeReady(UUID projectId) {
        ProjectDagPlanner.DagPlan plan = planner.plan(projectId);
        if (!plan.valid()) throw new IllegalStateException("Project DAG contains a cycle");
        if (plan.executableNow().isEmpty()) return new WaveExecution(projectId, List.of());

        Semaphore permits = new Semaphore(maxParallel);
        List<Future<ItemExecution>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ProjectDagPlanner.WorkItemView item : plan.executableNow()) {
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return runToGate(item.id());
                    } finally {
                        permits.release();
                    }
                }));
            }

            List<ItemExecution> results = new ArrayList<>();
            for (Future<ItemExecution> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    results.add(new ItemExecution(null, null, WorkItemStatus.FAILED, cause.getMessage()));
                }
            }
            return new WaveExecution(projectId, results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Wave execution interrupted", e);
        }
    }

    private ItemExecution runToGate(UUID id) {
        int transitions = 0;
        while (transitions++ < 12) {
            WorkItem current = workItems.findById(id).orElseThrow();
            if (isGate(current.getStatus())) {
                return new ItemExecution(current.getId(), current.getExternalId(), current.getStatus(), null);
            }
            workflow.process(id);
        }
        WorkItem current = workItems.findById(id).orElseThrow();
        return new ItemExecution(current.getId(), current.getExternalId(), current.getStatus(), "Transition safety limit reached");
    }

    private boolean isGate(WorkItemStatus status) {
        return status == WorkItemStatus.READY_FOR_HUMAN_REVIEW || status == WorkItemStatus.DONE || status == WorkItemStatus.FAILED;
    }

    public record WaveExecution(UUID projectId, List<ItemExecution> items) {}
    public record ItemExecution(UUID workItemId, String externalId, WorkItemStatus status, String error) {}
}
