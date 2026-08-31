package com.ordevia.aidev.session.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_checkpoint")
public class AgentCheckpoint {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "step_number", nullable = false) private int stepNumber;
    @Enumerated(EnumType.STRING) @Column(name = "checkpoint_type", nullable = false, length = 40) private AgentCheckpointType checkpointType;
    @Column(columnDefinition = "text") private String summary;
    @Column(name = "provider_turn_id", length = 500) private String providerTurnId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AgentCheckpoint() {}
    public AgentCheckpoint(UUID id, UUID sessionId, int sequenceNumber, int stepNumber, AgentCheckpointType type, String summary, String providerTurnId) {
        this.id=id; this.sessionId=sessionId; this.sequenceNumber=sequenceNumber; this.stepNumber=stepNumber; this.checkpointType=type; this.summary=summary; this.providerTurnId=providerTurnId; this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getSessionId(){return sessionId;} public int getSequenceNumber(){return sequenceNumber;}
    public int getStepNumber(){return stepNumber;} public AgentCheckpointType getCheckpointType(){return checkpointType;} public String getSummary(){return summary;}
    public String getProviderTurnId(){return providerTurnId;} public Instant getCreatedAt(){return createdAt;}
}
