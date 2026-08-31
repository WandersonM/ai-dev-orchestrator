package com.ordevia.aidev.workflow.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.execution.domain.AgentExecution;
import com.ordevia.aidev.execution.infrastructure.AgentExecutionJpaRepository;
import com.ordevia.aidev.workitem.domain.*;
import com.ordevia.aidev.workitem.infrastructure.WorkItemDependencyJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import com.ordevia.aidev.workspace.application.GitWorktreeManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.*;

@Service
public class WorkflowEngine {
    private final WorkItemJpaRepository workItems;
    private final WorkItemDependencyJpaRepository dependencies;
    private final AgentExecutionJpaRepository executions;
    private final Map<AgentType, Agent> agents;
    private final Path workspaceRoot;
    private final GitWorktreeManager worktrees;
    private final int maxReviewIterations;

    public WorkflowEngine(WorkItemJpaRepository workItems,
                          WorkItemDependencyJpaRepository dependencies,
                          AgentExecutionJpaRepository executions,
                          List<Agent> agentList,
                          GitWorktreeManager worktrees,
                          @Value("${aidev.workspace-root}") String workspaceRoot,
                          @Value("${aidev.agents.review.max-iterations:3}") int maxReviewIterations) {
        this.workItems = workItems;
        this.dependencies = dependencies;
        this.executions = executions;
        this.agents = new EnumMap<>(AgentType.class);
        agentList.forEach(a -> agents.put(a.type(), a));
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.worktrees = worktrees;
        this.maxReviewIterations = maxReviewIterations;
    }

    @Transactional
    public WorkItem process(UUID id) {
        WorkItem item = workItems.findById(id).orElseThrow(() -> new NoSuchElementException("WorkItem not found"));
        ensureDependenciesSatisfied(item);
        return switch (item.getStatus()) {
            case NEW -> refine(item);
            case READY_FOR_DEVELOPMENT, CHANGES_REQUESTED -> implement(item);
            case REVIEWING -> review(item);
            default -> throw new IllegalStateException("No executable transition for status " + item.getStatus());
        };
    }

    @Transactional
    public WorkItem markDone(UUID id) {
        WorkItem item = workItems.findById(id).orElseThrow(() -> new NoSuchElementException("WorkItem not found"));
        if (item.getStatus() != WorkItemStatus.READY_FOR_HUMAN_REVIEW) {
            throw new IllegalStateException("Only READY_FOR_HUMAN_REVIEW WorkItems can be marked DONE");
        }
        item.moveTo(WorkItemStatus.DONE);
        return workItems.save(item);
    }

    private void ensureDependenciesSatisfied(WorkItem item) {
        List<WorkItemDependency> blockers = dependencies.findByWorkItemId(item.getId());
        if (blockers.isEmpty()) return;
        List<String> pending = new ArrayList<>();
        for (WorkItemDependency dependency : blockers) {
            WorkItem blocker = workItems.findById(dependency.getBlockedByWorkItemId())
                    .orElseThrow(() -> new IllegalStateException("Missing blocker WorkItem: " + dependency.getBlockedByWorkItemId()));
            if (blocker.getStatus() != WorkItemStatus.DONE) {
                pending.add(Objects.toString(blocker.getExternalId(), blocker.getId().toString()) + "=" + blocker.getStatus());
            }
        }
        if (!pending.isEmpty()) throw new IllegalStateException("WorkItem is blocked by: " + String.join(", ", pending));
    }

    private WorkItem refine(WorkItem item) {
        item.moveTo(WorkItemStatus.REFINING);
        AgentResult result = executeAgent(item, AgentType.REFINER, repository(item), Map.of());
        if (result.success()) {
            item.setSpecification(result.output());
            item.moveTo(WorkItemStatus.READY_FOR_DEVELOPMENT);
        } else {
            item.moveTo(WorkItemStatus.FAILED);
        }
        return workItems.save(item);
    }

    private WorkItem implement(WorkItem item) {
        item.moveTo(WorkItemStatus.IMPLEMENTING);
        Path sourceRepo = repository(item);
        GitWorktreeManager.Worktree worktree = worktrees.create(sourceRepo, item.getExternalId());
        item.setBranchName(worktree.branch());
        AgentResult result = executeAgent(item, AgentType.BACKEND_DEVELOPER, worktree.path(), Map.of(
                "reviewReport", Objects.toString(item.getReviewReport(), "")
        ));
        if (result.success()) {
            item.setImplementationReport(result.output());
            item.moveTo(WorkItemStatus.REVIEWING);
        } else {
            item.moveTo(WorkItemStatus.FAILED);
        }
        return workItems.save(item);
    }

    private WorkItem review(WorkItem item) {
        Path sourceRepo = repository(item);
        GitWorktreeManager.Worktree worktree = worktrees.create(sourceRepo, item.getExternalId());
        String diff = worktrees.diff(worktree.path());
        AgentResult result = executeAgent(item, AgentType.REVIEWER, worktree.path(), Map.of(
                "implementationReport", Objects.toString(item.getImplementationReport(), ""),
                "gitDiff", diff
        ));
        if (!result.success()) {
            item.moveTo(WorkItemStatus.FAILED);
            return workItems.save(item);
        }

        item.setReviewReport(result.output());
        item.incrementReviewIterations();
        if (result.output().contains("DECISION: APPROVED")) {
            item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        } else if (result.output().contains("DECISION: HUMAN_REQUIRED") || item.getReviewIterations() >= maxReviewIterations) {
            item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        } else {
            item.moveTo(WorkItemStatus.CHANGES_REQUESTED);
        }
        return workItems.save(item);
    }

    private AgentResult executeAgent(WorkItem item, AgentType type, Path repo, Map<String, Object> metadata) {
        Agent agent = requiredAgent(type);
        AgentContext context = new AgentContext(item.getId(), repo, item.getBranchName(), item.getTitle(), item.getDescription(), item.getSpecification(), metadata);
        AgentExecution execution = new AgentExecution(UUID.randomUUID(), item.getId(), agent.type(), item.getTitle());
        executions.save(execution);
        AgentResult result = agent.execute(context);
        if (result.success()) execution.succeed(result.output()); else execution.fail(result.error());
        executions.save(execution);
        return result;
    }

    private Path repository(WorkItem item) {
        Path repo = workspaceRoot.resolve(item.getRepositoryPath()).normalize();
        if (!repo.startsWith(workspaceRoot)) throw new SecurityException("Repository outside workspace root");
        return repo;
    }

    private Agent requiredAgent(AgentType type) {
        Agent agent = agents.get(type);
        if (agent == null) throw new IllegalStateException("Agent not registered: " + type);
        return agent;
    }
}
