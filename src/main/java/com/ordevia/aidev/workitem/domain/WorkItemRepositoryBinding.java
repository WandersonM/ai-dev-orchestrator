package com.ordevia.aidev.workitem.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_item_repository", uniqueConstraints = @UniqueConstraint(name = "uk_work_item_repository", columnNames = {"work_item_id", "project_repository_id"}))
public class WorkItemRepositoryBinding {
    @Id private UUID id;
    @Column(name = "work_item_id", nullable = false) private UUID workItemId;
    @Column(name = "project_repository_id", nullable = false) private UUID projectRepositoryId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private WorkItemRepositoryPurpose purpose;
    @Column(name = "base_branch_override", length = 200) private String baseBranchOverride;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WorkItemRepositoryBinding() {}

    public WorkItemRepositoryBinding(UUID id, UUID workItemId, UUID projectRepositoryId, WorkItemRepositoryPurpose purpose, String baseBranchOverride) {
        this.id = id;
        this.workItemId = workItemId;
        this.projectRepositoryId = projectRepositoryId;
        this.purpose = purpose == null ? WorkItemRepositoryPurpose.OTHER : purpose;
        this.baseBranchOverride = baseBranchOverride;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkItemId() { return workItemId; }
    public UUID getProjectRepositoryId() { return projectRepositoryId; }
    public WorkItemRepositoryPurpose getPurpose() { return purpose; }
    public String getBaseBranchOverride() { return baseBranchOverride; }
    public Instant getCreatedAt() { return createdAt; }
}
