package com.ordevia.aidev.workflow.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.execution.domain.AgentExecution;
import com.ordevia.aidev.execution.infrastructure.AgentExecutionJpaRepository;
import com.ordevia.aidev.planning.application.PlanningService;
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
    private static final Set<String> VALID_DELIVERY_ROLES = Set.of("BACKEND", "FRONTEND", "INTEGRATION", "NONE");

    private final WorkItemJpaRepository workItems;
    private final WorkItemDependencyJpaRepository dependencies;
    private final AgentExecutionJpaRepository executions;
    private final Map<AgentType, Agent> agents;
    private final Path workspaceRoot;
    private final GitWorktreeManager worktrees;
    private final PlanningService planning;
    private final int maxReviewIterations;

    public WorkflowEngine(WorkItemJpaRepository workItems,
                          WorkItemDependencyJpaRepository dependencies,
                          AgentExecutionJpaRepository executions,
                          List<Agent> agentList,
                          GitWorktreeManager worktrees,
                          PlanningService planning,
                          @Value("${aidev.workspace-root}") String workspaceRoot,
                          @Value("${aidev.agents.review.max-iterations:3}") int maxReviewIterations) {
        this.workItems = workItems;
        this.dependencies = dependencies;
        this.executions = executions;
        this.agents = new EnumMap<>(AgentType.class);
        agentList.forEach(a -> agents.put(a.type(), a));
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.worktrees = worktrees;
        this.planning = planning;
        this.maxReviewIterations = maxReviewIterations;
    }

    @Transactional
    public WorkItem process(UUID id) {
        WorkItem item = requiredItem(id);
        ensureDependenciesSatisfied(item);
        return switch (item.getStatus()) {
            case NEW -> {
                planning.start(id);
                yield requiredItem(id);
            }
            case READY_FOR_DOMAIN_VALIDATION -> validateDomain(item);
            case READY_FOR_ARCHITECTURE -> architect(item);
            case READY_FOR_DEVELOPMENT, CHANGES_REQUESTED -> implement(item);
            case INTEGRATING -> integrate(item);
            case QA_VALIDATING -> qa(item);
            case REVIEWING -> review(item);
            case SECURITY_REVIEWING -> securityReview(item);
            case RELEASE_PREPARING -> releaseReadiness(item);
            default -> throw new IllegalStateException("No executable transition for status " + item.getStatus());
        };
    }

    @Transactional
    public WorkItem markDone(UUID id) {
        WorkItem item = requiredItem(id);
        if (item.getStatus() != WorkItemStatus.READY_FOR_HUMAN_REVIEW) {
            throw new IllegalStateException("Only READY_FOR_HUMAN_REVIEW WorkItems can be marked DONE after human merge approval");
        }
        item.moveTo(WorkItemStatus.DONE);
        return workItems.save(item);
    }

    @Transactional
    public WorkItem approveDomainOverride(UUID id) {
        WorkItem item = requiredItem(id);
        if (item.getStatus() != WorkItemStatus.DOMAIN_HUMAN_REQUIRED) {
            throw new IllegalStateException("WorkItem is not waiting for domain approval");
        }
        item.moveTo(WorkItemStatus.READY_FOR_ARCHITECTURE);
        return workItems.save(item);
    }

    @Transactional
    public WorkItem approveArchitectureOverride(UUID id) {
        WorkItem item = requiredItem(id);
        if (item.getStatus() != WorkItemStatus.ARCHITECTURE_HUMAN_REQUIRED) {
            throw new IllegalStateException("WorkItem is not waiting for architecture approval");
        }
        if (item.getDeliveryRoles() == null || item.getDeliveryRoles().isBlank()) item.setDeliveryRoles("BACKEND");
        item.moveTo(WorkItemStatus.READY_FOR_DEVELOPMENT);
        return workItems.save(item);
    }

    @Transactional
    public WorkItem approveReleaseOverride(UUID id) {
        WorkItem item = requiredItem(id);
        if (item.getStatus() != WorkItemStatus.RELEASE_HUMAN_REQUIRED) {
            throw new IllegalStateException("WorkItem is not waiting for release approval");
        }
        item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        return workItems.save(item);
    }

    private WorkItem validateDomain(WorkItem item) {
        item.moveTo(WorkItemStatus.DOMAIN_VALIDATING);
        workItems.save(item);
        AgentResult result = executeAgent(item, AgentType.DOMAIN_GUARDIAN, repository(item), Map.of());
        if (!result.success()) {
            item.moveTo(WorkItemStatus.FAILED);
        } else {
            item.setDomainValidationReport(result.output());
            if (result.output().contains("DECISION: APPROVED")) item.moveTo(WorkItemStatus.READY_FOR_ARCHITECTURE);
            else item.moveTo(WorkItemStatus.DOMAIN_HUMAN_REQUIRED);
        }
        return workItems.save(item);
    }

    private WorkItem architect(WorkItem item) {
        item.moveTo(WorkItemStatus.ARCHITECTING);
        workItems.save(item);
        AgentResult result = executeAgent(item, AgentType.ARCHITECT, repository(item), Map.of(
                "domainValidationReport", Objects.toString(item.getDomainValidationReport(), "")
        ));
        if (!result.success()) {
            item.moveTo(WorkItemStatus.FAILED);
            return workItems.save(item);
        }

        item.setArchitecturePlan(result.output());
        Set<String> roles = parseDeliveryRoles(result.output());
        if (!roles.isEmpty()) item.setDeliveryRoles(String.join(",", roles));

        if (result.output().contains("DECISION: HUMAN_REQUIRED") || roles.isEmpty()) {
            item.moveTo(WorkItemStatus.ARCHITECTURE_HUMAN_REQUIRED);
        } else {
            item.moveTo(WorkItemStatus.READY_FOR_DEVELOPMENT);
        }
        return workItems.save(item);
    }

    private WorkItem implement(WorkItem item) {
        item.moveTo(WorkItemStatus.IMPLEMENTING);
        Path sourceRepo = repository(item);
        GitWorktreeManager.Worktree worktree = worktrees.create(sourceRepo, item.getExternalId());
        item.setBranchName(worktree.branch());
        Set<String> roles = deliveryRoles(item);
        List<String> reports = new ArrayList<>();

        if (roles.contains("BACKEND")) {
            AgentResult backend = executeAgent(item, AgentType.BACKEND_DEVELOPER, worktree.path(), implementationMetadata(item));
            if (!backend.success()) return fail(item);
            reports.add("## Backend\n" + backend.output());
        }
        if (roles.contains("FRONTEND")) {
            AgentResult frontend = executeAgent(item, AgentType.FRONTEND_DEVELOPER, worktree.path(), implementationMetadata(item));
            if (!frontend.success()) return fail(item);
            reports.add("## Frontend\n" + frontend.output());
        }
        if (roles.contains("NONE")) reports.add("No code implementation role was required by the architecture plan.");

        item.setImplementationReport(String.join("\n\n", reports));
        item.moveTo(roles.contains("INTEGRATION") ? WorkItemStatus.INTEGRATING : WorkItemStatus.QA_VALIDATING);
        return workItems.save(item);
    }

    private WorkItem integrate(WorkItem item) {
        GitWorktreeManager.Worktree worktree = worktrees.create(repository(item), item.getExternalId());
        AgentResult result = executeAgent(item, AgentType.INTEGRATION_ENGINEER, worktree.path(), Map.of(
                "architecturePlan", Objects.toString(item.getArchitecturePlan(), ""),
                "implementationReport", Objects.toString(item.getImplementationReport(), ""),
                "qaReport", Objects.toString(item.getQaReport(), "")
        ));
        if (!result.success()) return fail(item);
        item.setIntegrationReport(result.output());
        if (result.output().contains("DECISION: CHANGES_REQUIRED")) item.moveTo(WorkItemStatus.CHANGES_REQUESTED);
        else if (result.output().contains("DECISION: HUMAN_REQUIRED")) item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        else item.moveTo(WorkItemStatus.QA_VALIDATING);
        return workItems.save(item);
    }

    private WorkItem qa(WorkItem item) {
        GitWorktreeManager.Worktree worktree = worktrees.create(repository(item), item.getExternalId());
        AgentResult result = executeAgent(item, AgentType.QA_ENGINEER, worktree.path(), Map.of(
                "architecturePlan", Objects.toString(item.getArchitecturePlan(), ""),
                "implementationReport", Objects.toString(item.getImplementationReport(), ""),
                "integrationReport", Objects.toString(item.getIntegrationReport(), "")
        ));
        if (!result.success()) return fail(item);
        item.setQaReport(result.output());
        if (result.output().contains("DECISION: CHANGES_REQUIRED")) item.moveTo(WorkItemStatus.CHANGES_REQUESTED);
        else if (result.output().contains("DECISION: HUMAN_REQUIRED")) item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        else item.moveTo(WorkItemStatus.REVIEWING);
        return workItems.save(item);
    }

    private WorkItem review(WorkItem item) {
        GitWorktreeManager.Worktree worktree = worktrees.create(repository(item), item.getExternalId());
        String diff = worktrees.diff(worktree.path());
        AgentResult result = executeAgent(item, AgentType.REVIEWER, worktree.path(), Map.of(
                "architecturePlan", Objects.toString(item.getArchitecturePlan(), ""),
                "implementationReport", Objects.toString(item.getImplementationReport(), ""),
                "integrationReport", Objects.toString(item.getIntegrationReport(), ""),
                "qaReport", Objects.toString(item.getQaReport(), ""),
                "gitDiff", diff
        ));
        if (!result.success()) return fail(item);

        item.setReviewReport(result.output());
        item.incrementReviewIterations();
        if (result.output().contains("DECISION: APPROVED")) {
            item.moveTo(WorkItemStatus.SECURITY_REVIEWING);
        } else if (result.output().contains("DECISION: HUMAN_REQUIRED") || item.getReviewIterations() >= maxReviewIterations) {
            item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        } else {
            item.moveTo(WorkItemStatus.CHANGES_REQUESTED);
        }
        return workItems.save(item);
    }

    private WorkItem securityReview(WorkItem item) {
        GitWorktreeManager.Worktree worktree = worktrees.create(repository(item), item.getExternalId());
        AgentResult result = executeAgent(item, AgentType.SECURITY_REVIEWER, worktree.path(), Map.of(
                "architecturePlan", Objects.toString(item.getArchitecturePlan(), ""),
                "implementationReport", Objects.toString(item.getImplementationReport(), ""),
                "gitDiff", worktrees.diff(worktree.path())
        ));
        if (!result.success()) return fail(item);
        item.setSecurityReport(result.output());
        if (result.output().contains("DECISION: APPROVED")) item.moveTo(WorkItemStatus.RELEASE_PREPARING);
        else if (result.output().contains("DECISION: HUMAN_REQUIRED")) item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        else item.moveTo(WorkItemStatus.CHANGES_REQUESTED);
        return workItems.save(item);
    }

    private WorkItem releaseReadiness(WorkItem item) {
        GitWorktreeManager.Worktree worktree = worktrees.create(repository(item), item.getExternalId());
        AgentResult result = executeAgent(item, AgentType.RELEASE_ENGINEER, worktree.path(), Map.of(
                "architecturePlan", Objects.toString(item.getArchitecturePlan(), ""),
                "implementationReport", Objects.toString(item.getImplementationReport(), ""),
                "qaReport", Objects.toString(item.getQaReport(), ""),
                "securityReport", Objects.toString(item.getSecurityReport(), "")
        ));
        if (!result.success()) return fail(item);
        item.setReleaseReport(result.output());
        if (result.output().contains("DECISION: READY")) item.moveTo(WorkItemStatus.READY_FOR_HUMAN_REVIEW);
        else item.moveTo(WorkItemStatus.RELEASE_HUMAN_REQUIRED);
        return workItems.save(item);
    }

    private Map<String, Object> implementationMetadata(WorkItem item) {
        return Map.of(
                "architecturePlan", Objects.toString(item.getArchitecturePlan(), ""),
                "reviewReport", Objects.toString(item.getReviewReport(), ""),
                "qaReport", Objects.toString(item.getQaReport(), ""),
                "securityReport", Objects.toString(item.getSecurityReport(), "")
        );
    }

    private Set<String> deliveryRoles(WorkItem item) {
        Set<String> roles = new LinkedHashSet<>();
        String raw = Objects.toString(item.getDeliveryRoles(), "BACKEND");
        for (String token : raw.split(",")) {
            String role = token.trim().toUpperCase(Locale.ROOT);
            if (VALID_DELIVERY_ROLES.contains(role)) roles.add(role);
        }
        if (roles.isEmpty()) roles.add("BACKEND");
        return roles;
    }

    private Set<String> parseDeliveryRoles(String architectureOutput) {
        if (architectureOutput == null) return Set.of();
        for (String line : architectureOutput.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("DELIVERY_ROLES:")) continue;
            String value = trimmed.substring("DELIVERY_ROLES:".length()).trim();
            Set<String> roles = new LinkedHashSet<>();
            for (String token : value.split(",")) {
                String role = token.trim().toUpperCase(Locale.ROOT);
                if (VALID_DELIVERY_ROLES.contains(role)) roles.add(role);
            }
            return roles;
        }
        return Set.of();
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

    private WorkItem fail(WorkItem item) {
        item.moveTo(WorkItemStatus.FAILED);
        return workItems.save(item);
    }

    private WorkItem requiredItem(UUID id) {
        return workItems.findById(id).orElseThrow(() -> new NoSuchElementException("WorkItem not found"));
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
