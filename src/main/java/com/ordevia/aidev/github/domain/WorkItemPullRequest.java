package com.ordevia.aidev.github.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_item_pull_request", uniqueConstraints = @UniqueConstraint(name = "uk_work_item_pr_repo", columnNames = {"work_item_id", "repository_alias"}))
public class WorkItemPullRequest {
    @Id private UUID id;
    @Column(name = "work_item_id", nullable = false) private UUID workItemId;
    @Column(name = "repository_alias", nullable = false, length = 80) private String repositoryAlias;
    @Column(name = "repository_slug", nullable = false, length = 300) private String repositorySlug;
    @Column(name = "pull_request_number", nullable = false) private int pullRequestNumber;
    @Column(name = "pull_request_url", nullable = false, columnDefinition = "text") private String pullRequestUrl;
    @Column(name = "head_branch", nullable = false, length = 300) private String headBranch;
    @Column(name = "base_branch", nullable = false, length = 300) private String baseBranch;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected WorkItemPullRequest() {}

    public WorkItemPullRequest(UUID id, UUID workItemId, String repositoryAlias, String repositorySlug,
                               int pullRequestNumber, String pullRequestUrl, String headBranch, String baseBranch) {
        this.id = id;
        this.workItemId = workItemId;
        this.repositoryAlias = repositoryAlias;
        this.repositorySlug = repositorySlug;
        this.pullRequestNumber = pullRequestNumber;
        this.pullRequestUrl = pullRequestUrl;
        this.headBranch = headBranch;
        this.baseBranch = baseBranch;
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getWorkItemId() { return workItemId; }
    public String getRepositoryAlias() { return repositoryAlias; }
    public String getRepositorySlug() { return repositorySlug; }
    public int getPullRequestNumber() { return pullRequestNumber; }
    public String getPullRequestUrl() { return pullRequestUrl; }
    public String getHeadBranch() { return headBranch; }
    public String getBaseBranch() { return baseBranch; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
