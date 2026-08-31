package com.ordevia.aidev.project.domain;

import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wave_execution_item")
public class WaveExecutionItem {
    @Id private UUID id;
    @Column(name = "wave_execution_id", nullable = false) private UUID waveExecutionId;
    @Column(name = "work_item_id", nullable = false) private UUID workItemId;
    @Enumerated(EnumType.STRING) @Column(name = "status_before", nullable = false, length = 60) private WorkItemStatus statusBefore;
    @Enumerated(EnumType.STRING) @Column(name = "status_after", length = 60) private WorkItemStatus statusAfter;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected WaveExecutionItem() {}

    public WaveExecutionItem(UUID id, UUID waveExecutionId, UUID workItemId, WorkItemStatus statusBefore) {
        this.id = id;
        this.waveExecutionId = waveExecutionId;
        this.workItemId = workItemId;
        this.statusBefore = statusBefore;
        this.startedAt = Instant.now();
    }

    public void finish(WorkItemStatus statusAfter, String errorMessage) {
        this.statusAfter = statusAfter;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWaveExecutionId() { return waveExecutionId; }
    public UUID getWorkItemId() { return workItemId; }
    public WorkItemStatus getStatusBefore() { return statusBefore; }
    public WorkItemStatus getStatusAfter() { return statusAfter; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
