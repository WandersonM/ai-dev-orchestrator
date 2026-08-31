package com.ordevia.aidev.workitem.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_item_dependency")
public class WorkItemDependency {
    @Id private UUID id;
    @Column(name = "work_item_id", nullable = false) private UUID workItemId;
    @Column(name = "blocked_by_work_item_id", nullable = false) private UUID blockedByWorkItemId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WorkItemDependency() {}

    public WorkItemDependency(UUID id, UUID workItemId, UUID blockedByWorkItemId) {
        if (workItemId.equals(blockedByWorkItemId)) throw new IllegalArgumentException("A WorkItem cannot block itself");
        this.id = id;
        this.workItemId = workItemId;
        this.blockedByWorkItemId = blockedByWorkItemId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWorkItemId() { return workItemId; }
    public UUID getBlockedByWorkItemId() { return blockedByWorkItemId; }
    public Instant getCreatedAt() { return createdAt; }
}
