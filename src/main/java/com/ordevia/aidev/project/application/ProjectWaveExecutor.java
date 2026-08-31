package com.ordevia.aidev.project.application;

import com.ordevia.aidev.project.domain.WaveExecution;
import com.ordevia.aidev.project.domain.WaveExecutionItem;
import com.ordevia.aidev.project.domain.WaveExecutionStatus;
import com.ordevia.aidev.project.infrastructure.WaveExecutionItemJpaRepository;
import com.ordevia.aidev.project.infrastructure.WaveExecutionJpaRepository;
import com.ordevia.aidev.workflow.application.WorkflowEngine;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class ProjectWaveExecutor {
    private final ProjectDagPlanner planner;
    private final WorkflowEngine workflow;
    private final WorkItemJpaRepository workItems;
    private final WaveExecutionJpaRepository waveExecutions;
    private final WaveExecutionItemJpaRepository waveExecutionItems;
    private final int maxParallel;

    public ProjectWaveExecutor(ProjectDagPlanner planner,
                               WorkflowEngine workflow,
                               WorkItemJpaRepository workItems,
                               WaveExecutionJpaRepository waveExecutions,
                               WaveExecutionItemJpaRepository waveExecutionItems,
                               @Value("${aidev.orchestration.max-parallel:3}") int maxParallel) {
        this.planner = planner;
        this.workflow = workflow;
        this.workItems = workItems;
        this.waveExecutions = waveExecutions;
        this.waveExecutionItems = waveExecutionItems;
        this.maxParallel = Math.max(1, maxParallel);
    }

    public WaveRunResult executeReady(UUID projectId) {
        ProjectDagPlanner.DagPlan plan = planner.plan(projectId);
        if (!plan.valid()) throw new IllegalStateException("Project DAG contains a cycle");
        if (plan.executableNow().isEmpty()) return new WaveRunResult(null, projectId, List.of());

        WaveExecution audit = waveExecutions.save(new WaveExecution(UUID.randomUUID(), projectId, maxParallel));
        Semaphore permits = new Semaphore(maxParallel);
        List<Future<ItemExecution>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ProjectDagPlanner.WorkItemView item : plan.executableNow()) {
                WaveExecutionItem itemAudit = waveExecutionItems.save(new WaveExecutionItem(
                        UUID.randomUUID(), audit.getId(), item.id(), item.status()));
                futures.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        ItemExecution result = runToGate(item.id());
                        itemAudit.finish(result.status(), result.error());
                        waveExecutionItems.save(itemAudit);
                        return result;
                    } catch (Exception e) {
                        WorkItemStatus currentStatus = workItems.findById(item.id()).map(WorkItem::getStatus).orElse(WorkItemStatus.FAILED);
                        itemAudit.finish(currentStatus, e.getMessage());
                        waveExecutionItems.save(itemAudit);
                        return new ItemExecution(item.id(), item.externalId(), currentStatus, e.getMessage());
                    } finally {
                        permits.release();
                    }
                }));
            }

            List<ItemExecution> results = new ArrayList<>();
            for (Future<ItemExecution> future : futures) results.add(future.get());
            audit.finish(overallStatus(results));
            waveExecutions.save(audit);
            return new WaveRunResult(audit.getId(), projectId, results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            audit.finish(WaveExecutionStatus.FAILED);
            waveExecutions.save(audit);
            throw new IllegalStateException("Wave execution interrupted", e);
        } catch (ExecutionException e) {
            audit.finish(WaveExecutionStatus.FAILED);
            waveExecutions.save(audit);
            throw new IllegalStateException("Wave execution failed", e.getCause());
        }
    }

    private ItemExecution runToGate(UUID id) {
        int transitions = 0;
        while (transitions++ < 12) {
            WorkItem current = workItems.findById(id).orElseThrow();
            if (isGate(current.getStatus())) return new ItemExecution(current.getId(), current.getExternalId(), current.getStatus(), null);
            workflow.process(id);
        }
        WorkItem current = workItems.findById(id).orElseThrow();
        return new ItemExecution(current.getId(), current.getExternalId(), current.getStatus(), "Transition safety limit reached");
    }

    private WaveExecutionStatus overallStatus(List<ItemExecution> results) {
        long failed = results.stream().filter(result -> result.error() != null || result.status() == WorkItemStatus.FAILED).count();
        if (failed == 0) return WaveExecutionStatus.COMPLETED;
        if (failed == results.size()) return WaveExecutionStatus.FAILED;
        return WaveExecutionStatus.PARTIAL_FAILURE;
    }

    private boolean isGate(WorkItemStatus status) {
        return status == WorkItemStatus.READY_FOR_HUMAN_REVIEW || status == WorkItemStatus.DONE || status == WorkItemStatus.FAILED;
    }

    public record WaveRunResult(UUID waveExecutionId, UUID projectId, List<ItemExecution> items) {}
    public record ItemExecution(UUID workItemId, String externalId, WorkItemStatus status, String error) {}
}
