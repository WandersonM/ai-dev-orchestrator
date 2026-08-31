package com.ordevia.aidev.telemetry.domain;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.llm.domain.LlmProvider;
import com.ordevia.aidev.llm.domain.LlmTask;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="llm_call_metric")
public class LlmCallMetric {
    @Id private UUID id;
    @Column(name="work_item_id") private UUID workItemId;
    @Column(name="session_id") private UUID sessionId;
    @Enumerated(EnumType.STRING) @Column(name="agent_type",length=80) private AgentType agentType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=80) private LlmTask task;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private LlmProvider provider;
    @Column(nullable=false,length=160) private String model;
    @Column(name="input_tokens",nullable=false) private long inputTokens;
    @Column(name="output_tokens",nullable=false) private long outputTokens;
    @Column(name="cached_tokens",nullable=false) private long cachedTokens;
    @Column(name="total_tokens",nullable=false) private long totalTokens;
    @Column(name="latency_ms",nullable=false) private long latencyMs;
    @Column(name="estimated_cost_usd",nullable=false,precision=18,scale=8) private BigDecimal estimatedCostUsd;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected LlmCallMetric() {}
    public LlmCallMetric(UUID id,UUID workItemId,UUID sessionId,AgentType agentType,LlmTask task,LlmProvider provider,String model,
                         long inputTokens,long outputTokens,long cachedTokens,long totalTokens,long latencyMs,BigDecimal estimatedCostUsd){
        this.id=id;this.workItemId=workItemId;this.sessionId=sessionId;this.agentType=agentType;this.task=task;this.provider=provider;this.model=model;
        this.inputTokens=inputTokens;this.outputTokens=outputTokens;this.cachedTokens=cachedTokens;this.totalTokens=totalTokens;this.latencyMs=latencyMs;
        this.estimatedCostUsd=estimatedCostUsd;this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getWorkItemId(){return workItemId;} public UUID getSessionId(){return sessionId;} public AgentType getAgentType(){return agentType;}
    public LlmTask getTask(){return task;} public LlmProvider getProvider(){return provider;} public String getModel(){return model;}
    public long getInputTokens(){return inputTokens;} public long getOutputTokens(){return outputTokens;} public long getCachedTokens(){return cachedTokens;} public long getTotalTokens(){return totalTokens;}
    public long getLatencyMs(){return latencyMs;} public BigDecimal getEstimatedCostUsd(){return estimatedCostUsd;} public Instant getCreatedAt(){return createdAt;}
}
