package com.ordevia.aidev.session.domain;

import com.ordevia.aidev.agent.domain.AgentType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_session")
public class AgentSession {
    @Id private UUID id;
    @Column(name = "work_item_id", nullable = false) private UUID workItemId;
    @Enumerated(EnumType.STRING) @Column(name = "agent_type", nullable = false, length = 80) private AgentType agentType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AgentSessionStatus status;
    @Version @Column(nullable = false) private long version;
    @Column(name = "current_step", nullable = false) private int currentStep;
    @Column(name = "checkpoint_seq", nullable = false) private int checkpointSequence;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "paused_at") private Instant pausedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "last_heartbeat_at") private Instant lastHeartbeatAt;
    @Column(name = "last_error", columnDefinition = "text") private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AgentSession() {}

    public AgentSession(UUID id, UUID workItemId, AgentType agentType) {
        this.id = id;
        this.workItemId = workItemId;
        this.agentType = agentType;
        this.status = AgentSessionStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void start() {
        if (status != AgentSessionStatus.CREATED && status != AgentSessionStatus.PAUSED) {
            throw new IllegalStateException("Session cannot start from " + status);
        }
        status = AgentSessionStatus.RUNNING;
        if (startedAt == null) startedAt = Instant.now();
        pausedAt = null;
        heartbeat();
    }

    public void requestPause() {
        requireNonTerminal();
        if (status == AgentSessionStatus.PAUSED || status == AgentSessionStatus.PAUSE_REQUESTED) return;
        if (status != AgentSessionStatus.RUNNING && status != AgentSessionStatus.CREATED) {
            throw new IllegalStateException("Session cannot pause from " + status);
        }
        status = AgentSessionStatus.PAUSE_REQUESTED;
        touch();
    }

    public void markPaused() {
        if (status != AgentSessionStatus.PAUSE_REQUESTED) throw new IllegalStateException("Pause was not requested");
        status = AgentSessionStatus.PAUSED;
        pausedAt = Instant.now();
        touch();
    }

    public void resume() {
        if (status != AgentSessionStatus.PAUSED && status != AgentSessionStatus.PAUSE_REQUESTED) {
            throw new IllegalStateException("Session cannot resume from " + status);
        }
        status = AgentSessionStatus.RUNNING;
        pausedAt = null;
        heartbeat();
    }

    public void requestCancel() {
        if (status.terminal()) return;
        status = AgentSessionStatus.CANCEL_REQUESTED;
        touch();
    }

    public void markCancelled() {
        if (status != AgentSessionStatus.CANCEL_REQUESTED) throw new IllegalStateException("Cancel was not requested");
        status = AgentSessionStatus.CANCELLED;
        finishedAt = Instant.now();
        touch();
    }

    public void complete() {
        requireNonTerminal();
        status = AgentSessionStatus.COMPLETED;
        finishedAt = Instant.now();
        touch();
    }

    public void fail(String error) {
        if (status.terminal()) return;
        status = AgentSessionStatus.FAILED;
        lastError = error;
        finishedAt = Instant.now();
        touch();
    }

    public int nextCheckpointSequence() {
        checkpointSequence++;
        touch();
        return checkpointSequence;
    }

    public void step(int step) {
        currentStep = Math.max(currentStep, step);
        heartbeat();
    }

    public void heartbeat() {
        lastHeartbeatAt = Instant.now();
        touch();
    }

    private void requireNonTerminal() {
        if (status.terminal()) throw new IllegalStateException("Session is terminal: " + status);
    }

    private void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getWorkItemId() { return workItemId; }
    public AgentType getAgentType() { return agentType; }
    public AgentSessionStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public int getCurrentStep() { return currentStep; }
    public int getCheckpointSequence() { return checkpointSequence; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getPausedAt() { return pausedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
