package com.ordevia.aidev.session.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_session_message")
public class AgentSessionMessage {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AgentSessionMessageRole role;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(name = "provided_by", length = 200) private String providedBy;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AgentSessionMessage() {}
    public AgentSessionMessage(UUID id, UUID sessionId, AgentSessionMessageRole role, String content, String providedBy) {
        this.id = id; this.sessionId = sessionId; this.role = role; this.content = content; this.providedBy = providedBy; this.createdAt = Instant.now();
    }
    public void markConsumed() { if (consumedAt == null) consumedAt = Instant.now(); }
    public UUID getId(){return id;} public UUID getSessionId(){return sessionId;} public AgentSessionMessageRole getRole(){return role;}
    public String getContent(){return content;} public String getProvidedBy(){return providedBy;} public Instant getConsumedAt(){return consumedAt;} public Instant getCreatedAt(){return createdAt;}
}
