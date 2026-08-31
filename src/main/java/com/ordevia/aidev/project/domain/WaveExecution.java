package com.ordevia.aidev.project.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wave_execution")
public class WaveExecution {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private WaveExecutionStatus status;
    @Column(name = "max_parallel", nullable = false) private int maxParallel;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected WaveExecution() {}

    public WaveExecution(UUID id, UUID projectId, int maxParallel) {
        this.id = id;
        this.projectId = projectId;
        this.maxParallel = maxParallel;
        this.status = WaveExecutionStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void finish(WaveExecutionStatus status) {
        this.status = status;
        this.finishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public WaveExecutionStatus getStatus() { return status; }
    public int getMaxParallel() { return maxParallel; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
