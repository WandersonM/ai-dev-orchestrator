package com.ordevia.aidev.governance.application;

import com.ordevia.aidev.telemetry.application.LlmTelemetryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class WorkItemBudgetService {
    private final LlmTelemetryService telemetry;
    private final long maxCalls;
    private final long maxTokens;
    private final long maxLlmLatencyMs;
    private final BigDecimal maxCostUsd;

    public WorkItemBudgetService(LlmTelemetryService telemetry,
                                 @Value("${aidev.budgets.work-item.max-llm-calls:100}") long maxCalls,
                                 @Value("${aidev.budgets.work-item.max-tokens:2000000}") long maxTokens,
                                 @Value("${aidev.budgets.work-item.max-llm-latency-ms:3600000}") long maxLlmLatencyMs,
                                 @Value("${aidev.budgets.work-item.max-cost-usd:25.00}") BigDecimal maxCostUsd) {
        this.telemetry=telemetry;this.maxCalls=maxCalls;this.maxTokens=maxTokens;this.maxLlmLatencyMs=maxLlmLatencyMs;this.maxCostUsd=maxCostUsd;
    }

    public void assertWithinBudget(UUID workItemId) {
        LlmTelemetryService.Summary s=telemetry.workItemSummary(workItemId);
        if(maxCalls>0 && s.calls()>=maxCalls) throw new BudgetExceededException("LLM call budget exceeded: "+s.calls()+"/"+maxCalls);
        if(maxTokens>0 && s.totalTokens()>=maxTokens) throw new BudgetExceededException("Token budget exceeded: "+s.totalTokens()+"/"+maxTokens);
        if(maxLlmLatencyMs>0 && s.llmLatencyMs()>=maxLlmLatencyMs) throw new BudgetExceededException("LLM time budget exceeded: "+s.llmLatencyMs()+"/"+maxLlmLatencyMs+" ms");
        if(maxCostUsd!=null && maxCostUsd.signum()>0 && s.estimatedCostUsd().compareTo(maxCostUsd)>=0) throw new BudgetExceededException("Estimated cost budget exceeded: $"+s.estimatedCostUsd()+"/$"+maxCostUsd);
    }

    public BudgetStatus status(UUID workItemId) {
        var s=telemetry.workItemSummary(workItemId);
        return new BudgetStatus(s,maxCalls,maxTokens,maxLlmLatencyMs,maxCostUsd,
                s.calls()<maxCalls && s.totalTokens()<maxTokens && s.llmLatencyMs()<maxLlmLatencyMs && s.estimatedCostUsd().compareTo(maxCostUsd)<0);
    }

    public record BudgetStatus(LlmTelemetryService.Summary usage,long maxCalls,long maxTokens,long maxLlmLatencyMs,BigDecimal maxCostUsd,boolean withinBudget){}
    public static final class BudgetExceededException extends RuntimeException { public BudgetExceededException(String message){super(message);} }
}
