package com.ordevia.aidev.workitem.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_item")
public class WorkItem {
    @Id private UUID id;
    @Column(name = "external_id") private String externalId;
    @Column(nullable = false, length = 300) private String title;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 60) private WorkItemStatus status;
    @Column(name = "repository_path", nullable = false, columnDefinition = "text") private String repositoryPath;
    @Column(name = "branch_name") private String branchName;
    @Column(columnDefinition = "text") private String specification;
    @Column(name = "implementation_report", columnDefinition = "text") private String implementationReport;
    @Column(name = "review_report", columnDefinition = "text") private String reviewReport;
    @Column(name = "review_iterations", nullable = false) private int reviewIterations;
    @Column(name = "pull_request_number") private Integer pullRequestNumber;
    @Column(name = "pull_request_url", columnDefinition = "text") private String pullRequestUrl;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected WorkItem() {}

    public WorkItem(UUID id, String externalId, String title, String description, String repositoryPath) {
        this.id = id;
        this.externalId = externalId;
        this.title = title;
        this.description = description;
        this.repositoryPath = repositoryPath;
        this.status = WorkItemStatus.NEW;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void moveTo(WorkItemStatus status) { this.status = status; touch(); }
    public void setSpecification(String specification) { this.specification = specification; touch(); }
    public void setImplementationReport(String implementationReport) { this.implementationReport = implementationReport; touch(); }
    public void setReviewReport(String reviewReport) { this.reviewReport = reviewReport; touch(); }
    public void setBranchName(String branchName) { this.branchName = branchName; touch(); }
    public void incrementReviewIterations() { reviewIterations++; touch(); }

    public void markPublished(int pullRequestNumber, String pullRequestUrl) {
        this.pullRequestNumber = pullRequestNumber;
        this.pullRequestUrl = pullRequestUrl;
        this.publishedAt = Instant.now();
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public WorkItemStatus getStatus() { return status; }
    public String getRepositoryPath() { return repositoryPath; }
    public String getBranchName() { return branchName; }
    public String getSpecification() { return specification; }
    public String getImplementationReport() { return implementationReport; }
    public String getReviewReport() { return reviewReport; }
    public int getReviewIterations() { return reviewIterations; }
    public Integer getPullRequestNumber() { return pullRequestNumber; }
    public String getPullRequestUrl() { return pullRequestUrl; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
