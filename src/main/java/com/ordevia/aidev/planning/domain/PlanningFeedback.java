package com.ordevia.aidev.planning.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planning_feedback")
public class PlanningFeedback {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(nullable = false) private int round;
    @Column(nullable = false, columnDefinition = "text") private String feedback;
    @Column(name = "provided_by", nullable = false, length = 200) private String providedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PlanningFeedback() {}

    public PlanningFeedback(UUID id, UUID sessionId, int round, String feedback, String providedBy) {
        if (feedback == null || feedback.isBlank()) throw new IllegalArgumentException("Feedback must not be blank");
        this.id = id;
        this.sessionId = sessionId;
        this.round = round;
        this.feedback = feedback.trim();
        this.providedBy = providedBy == null || providedBy.isBlank() ? "human" : providedBy.trim();
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public int getRound() { return round; }
    public String getFeedback() { return feedback; }
    public String getProvidedBy() { return providedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
