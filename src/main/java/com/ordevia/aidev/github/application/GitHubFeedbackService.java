package com.ordevia.aidev.github.application;

import com.ordevia.aidev.audit.application.AuditService;
import com.ordevia.aidev.github.domain.WorkItemPullRequest;
import com.ordevia.aidev.github.infrastructure.WorkItemPullRequestJpaRepository;
import com.ordevia.aidev.session.application.AgentSessionService;
import com.ordevia.aidev.session.domain.AgentSession;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
public class GitHubFeedbackService {
    private final WorkItemPullRequestJpaRepository pullRequests;
    private final WorkItemJpaRepository workItems;
    private final AgentSessionService sessions;
    private final AuditService audit;

    public GitHubFeedbackService(WorkItemPullRequestJpaRepository pullRequests,
                                 WorkItemJpaRepository workItems,
                                 AgentSessionService sessions,
                                 AuditService audit) {
        this.pullRequests = pullRequests;
        this.workItems = workItems;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public FeedbackResult review(String repositorySlug, int pullRequestNumber, String state, String body, String author) {
        WorkItemPullRequest pr = required(repositorySlug, pullRequestNumber);
        WorkItem item = workItems.findById(pr.getWorkItemId()).orElseThrow();
        boolean changesRequested = "CHANGES_REQUESTED".equalsIgnoreCase(Objects.toString(state, ""));
        boolean delivered = deliverToActiveSession(item, message("GitHub review", state, body, author));
        if (changesRequested && item.getStatus() != WorkItemStatus.DONE) {
            item.moveTo(WorkItemStatus.CHANGES_REQUESTED);
            workItems.save(item);
        }
        audit.append(item.getId(), null, changesRequested ? "GITHUB_CHANGES_REQUESTED" : "GITHUB_REVIEW_RECEIVED",
                "HUMAN", author, "PullRequest", repositorySlug + "#" + pullRequestNumber,
                Map.of("state", Objects.toString(state, ""), "body", Objects.toString(body, ""), "deliveredToAgent", delivered));
        return new FeedbackResult(item.getId(), delivered, item.getStatus());
    }

    @Transactional
    public FeedbackResult comment(String repositorySlug, int pullRequestNumber, String body, String author) {
        WorkItemPullRequest pr = required(repositorySlug, pullRequestNumber);
        WorkItem item = workItems.findById(pr.getWorkItemId()).orElseThrow();
        boolean delivered = deliverToActiveSession(item, message("GitHub PR comment", null, body, author));
        audit.append(item.getId(), null, "GITHUB_COMMENT_RECEIVED", "HUMAN", author,
                "PullRequest", repositorySlug + "#" + pullRequestNumber,
                Map.of("body", Objects.toString(body, ""), "deliveredToAgent", delivered));
        return new FeedbackResult(item.getId(), delivered, item.getStatus());
    }

    private boolean deliverToActiveSession(WorkItem item, String content) {
        Optional<AgentSession> active = sessions.list(item.getId()).stream().filter(s -> s.getStatus().active()).findFirst();
        if (active.isEmpty()) return false;
        sessions.addHumanMessage(active.get().getId(), content, "GitHub");
        return true;
    }

    private WorkItemPullRequest required(String repositorySlug, int pullRequestNumber) {
        return pullRequests.findByRepositorySlugAndPullRequestNumber(repositorySlug, pullRequestNumber)
                .orElseThrow(() -> new NoSuchElementException("Pull request is not linked to a WorkItem"));
    }

    private String message(String kind, String state, String body, String author) {
        return kind + " by " + Objects.toString(author, "unknown")
                + (state == null ? "" : " [" + state + "]") + ":\n" + Objects.toString(body, "");
    }

    public record FeedbackResult(java.util.UUID workItemId, boolean deliveredToActiveAgent, WorkItemStatus status) {}
}
