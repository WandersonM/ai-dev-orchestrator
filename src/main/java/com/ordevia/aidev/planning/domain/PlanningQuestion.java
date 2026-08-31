package com.ordevia.aidev.planning.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planning_question")
public class PlanningQuestion {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(nullable = false) private int round;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) private PlanningQuestionCategory category;
    @Column(nullable = false, columnDefinition = "text") private String question;
    @Column(nullable = false, columnDefinition = "text") private String rationale;
    @Column(nullable = false) private boolean blocking;
    @Column(name = "options_json", columnDefinition = "text") private String optionsJson;
    @Column(columnDefinition = "text") private String answer;
    @Column(name = "answered_by", length = 200) private String answeredBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "answered_at") private Instant answeredAt;

    protected PlanningQuestion() {}

    public PlanningQuestion(UUID id, UUID sessionId, int round, PlanningQuestionCategory category,
                            String question, String rationale, boolean blocking, String optionsJson) {
        this.id = id;
        this.sessionId = sessionId;
        this.round = round;
        this.category = category;
        this.question = question;
        this.rationale = rationale;
        this.blocking = blocking;
        this.optionsJson = optionsJson;
        this.createdAt = Instant.now();
    }

    public void answer(String answer, String answeredBy) {
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("Answer must not be blank");
        this.answer = answer.trim();
        this.answeredBy = answeredBy == null || answeredBy.isBlank() ? "human" : answeredBy.trim();
        this.answeredAt = Instant.now();
    }

    public boolean answered() { return answer != null && !answer.isBlank(); }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public int getRound() { return round; }
    public PlanningQuestionCategory getCategory() { return category; }
    public String getQuestion() { return question; }
    public String getRationale() { return rationale; }
    public boolean isBlocking() { return blocking; }
    public String getOptionsJson() { return optionsJson; }
    public String getAnswer() { return answer; }
    public String getAnsweredBy() { return answeredBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAnsweredAt() { return answeredAt; }
}
