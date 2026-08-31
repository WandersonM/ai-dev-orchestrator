package com.ordevia.aidev.execution.domain;

import com.ordevia.aidev.agent.domain.AgentType;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "agent_execution")
public class AgentExecution {
    @Id private UUID id;
    @Column(name="work_item_id", nullable=false) private UUID workItemId;
    @Enumerated(EnumType.STRING) @Column(name="agent_type", nullable=false) private AgentType agentType;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AgentExecutionStatus status;
    @Column(name="input_summary", columnDefinition="text") private String inputSummary;
    @Column(name="output_summary", columnDefinition="text") private String outputSummary;
    @Column(name="error_message", columnDefinition="text") private String errorMessage;
    @Column(name="started_at", nullable=false) private Instant startedAt;
    @Column(name="finished_at") private Instant finishedAt;
    @Column(name="duration_ms") private Long durationMs;
    protected AgentExecution() {}
    public AgentExecution(UUID id, UUID workItemId, AgentType type, String inputSummary) { this.id=id; this.workItemId=workItemId; this.agentType=type; this.inputSummary=inputSummary; this.status=AgentExecutionStatus.RUNNING; this.startedAt=Instant.now(); }
    public void succeed(String output) { finish(AgentExecutionStatus.SUCCEEDED, output, null); }
    public void fail(String error) { finish(AgentExecutionStatus.FAILED, null, error); }
    private void finish(AgentExecutionStatus status, String output, String error) { this.status=status; this.outputSummary=output; this.errorMessage=error; this.finishedAt=Instant.now(); this.durationMs=Duration.between(startedAt, finishedAt).toMillis(); }
}
