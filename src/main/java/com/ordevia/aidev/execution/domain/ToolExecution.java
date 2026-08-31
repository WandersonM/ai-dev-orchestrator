package com.ordevia.aidev.execution.domain;

import com.ordevia.aidev.agent.domain.AgentType;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_execution")
public class ToolExecution {
    @Id private UUID id;
    @Column(name = "work_item_id", nullable = false) private UUID workItemId;
    @Column(name = "session_id") private UUID sessionId;
    @Enumerated(EnumType.STRING) @Column(name = "agent_type", nullable = false, length = 60) private AgentType agentType;
    @Column(name = "step_number", nullable = false) private int stepNumber;
    @Column(name = "tool_name", nullable = false, length = 120) private String toolName;
    @Column(name = "arguments_json", nullable = false, columnDefinition = "text") private String argumentsJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ToolExecutionStatus status;
    @Column(name = "output_text", columnDefinition = "text") private String outputText;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "duration_ms") private Long durationMs;

    protected ToolExecution() {}

    public ToolExecution(UUID id, UUID workItemId, AgentType agentType, int stepNumber, String toolName, String argumentsJson) {
        this(id, workItemId, null, agentType, stepNumber, toolName, argumentsJson);
    }

    public ToolExecution(UUID id, UUID workItemId, UUID sessionId, AgentType agentType, int stepNumber, String toolName, String argumentsJson) {
        this.id=id; this.workItemId=workItemId; this.sessionId=sessionId; this.agentType=agentType; this.stepNumber=stepNumber;
        this.toolName=toolName; this.argumentsJson=argumentsJson; this.status=ToolExecutionStatus.RUNNING; this.startedAt=Instant.now();
    }

    public void succeed(String outputText) { finish(ToolExecutionStatus.SUCCEEDED, outputText, null); }
    public void fail(String errorMessage) { finish(ToolExecutionStatus.FAILED, null, errorMessage); }
    private void finish(ToolExecutionStatus status, String outputText, String errorMessage) {
        this.status=status; this.outputText=outputText; this.errorMessage=errorMessage; this.finishedAt=Instant.now();
        this.durationMs=Duration.between(startedAt, finishedAt).toMillis();
    }

    public UUID getId(){return id;} public UUID getWorkItemId(){return workItemId;} public UUID getSessionId(){return sessionId;}
    public AgentType getAgentType(){return agentType;} public int getStepNumber(){return stepNumber;} public String getToolName(){return toolName;}
    public String getArgumentsJson(){return argumentsJson;} public ToolExecutionStatus getStatus(){return status;}
    public String getOutputText(){return outputText;} public String getErrorMessage(){return errorMessage;}
    public Instant getStartedAt(){return startedAt;} public Instant getFinishedAt(){return finishedAt;} public Long getDurationMs(){return durationMs;}
}
