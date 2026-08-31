package com.ordevia.aidev.planning.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planning_session")
public class PlanningSession {
    @Id private UUID id;
    @Column(name = "work_item_id", nullable = false, unique = true) private UUID workItemId;
    @Version @Column(nullable = false) private long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private PlanningStatus status;
    @Column(nullable = false) private int round;
    @Column(name = "max_rounds", nullable = false) private int maxRounds;
    @Column(name = "latest_summary", columnDefinition = "text") private String latestSummary;
    @Column(name = "last_analysis_json", columnDefinition = "text") private String lastAnalysisJson;
    @Column(name = "final_specification", columnDefinition = "text") private String finalSpecification;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "approved_at") private Instant approvedAt;

    protected PlanningSession() {}

    public PlanningSession(UUID id, UUID workItemId, int maxRounds) {
        this.id = id;
        this.workItemId = workItemId;
        this.maxRounds = maxRounds;
        this.status = PlanningStatus.DISCOVERING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void startRound() {
        if (round >= maxRounds) throw new IllegalStateException("Planning session reached max rounds: " + maxRounds);
        round++;
        status = PlanningStatus.DISCOVERING;
        touch();
    }

    public void waitForInput(String summary, String rawJson) {
        latestSummary = summary;
        lastAnalysisJson = rawJson;
        status = PlanningStatus.WAITING_FOR_USER_INPUT;
        touch();
    }

    public void readyForReview(String summary, String rawJson, String specification) {
        latestSummary = summary;
        lastAnalysisJson = rawJson;
        finalSpecification = specification;
        status = PlanningStatus.READY_FOR_REVIEW;
        touch();
    }

    public void requireHuman(String summary, String rawJson) {
        latestSummary = summary;
        lastAnalysisJson = rawJson;
        status = PlanningStatus.HUMAN_REQUIRED;
        touch();
    }

    public void approve() {
        if (status != PlanningStatus.READY_FOR_REVIEW) throw new IllegalStateException("Planning is not ready for approval");
        status = PlanningStatus.APPROVED;
        approvedAt = Instant.now();
        touch();
    }

    public void fail(String rawJson) {
        lastAnalysisJson = rawJson;
        status = PlanningStatus.FAILED;
        touch();
    }

    public boolean canStartAnotherRound() { return round < maxRounds; }
    private void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getWorkItemId() { return workItemId; }
    public long getVersion() { return version; }
    public PlanningStatus getStatus() { return status; }
    public int getRound() { return round; }
    public int getMaxRounds() { return maxRounds; }
    public String getLatestSummary() { return latestSummary; }
    public String getLastAnalysisJson() { return lastAnalysisJson; }
    public String getFinalSpecification() { return finalSpecification; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
