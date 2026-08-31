package com.ordevia.aidev.telemetry.application;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.llm.domain.*;
import com.ordevia.aidev.llm.infrastructure.LlmPricingProperties;
import com.ordevia.aidev.telemetry.domain.LlmCallMetric;
import com.ordevia.aidev.telemetry.infrastructure.LlmCallMetricJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class LlmTelemetryService {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private final LlmCallMetricJpaRepository metrics;
    private final LlmPricingProperties pricing;

    public LlmTelemetryService(LlmCallMetricJpaRepository metrics,LlmPricingProperties pricing){this.metrics=metrics;this.pricing=pricing;}

    @Transactional
    public LlmCallMetric record(UUID workItemId,UUID sessionId,AgentType agentType,LlmTask task,LlmToolResponse response){
        LlmUsage usage=response.usage()==null?LlmUsage.empty():response.usage();
        LlmCallMetric metric=new LlmCallMetric(UUID.randomUUID(),workItemId,sessionId,agentType,task,response.provider(),response.model(),
                usage.inputTokens(),usage.outputTokens(),usage.cachedTokens(),usage.totalTokens(),response.latencyMs(),estimate(response.model(),usage));
        return metrics.save(metric);
    }

    @Transactional(readOnly=true)
    public Summary workItemSummary(UUID workItemId){return summarize(metrics.findByWorkItemIdOrderByCreatedAtAsc(workItemId));}
    @Transactional(readOnly=true)
    public Summary sessionSummary(UUID sessionId){return summarize(metrics.findBySessionIdOrderByCreatedAtAsc(sessionId));}
    @Transactional(readOnly=true) public List<LlmCallMetric> byWorkItem(UUID id){return metrics.findByWorkItemIdOrderByCreatedAtAsc(id);}
    @Transactional(readOnly=true) public List<LlmCallMetric> bySession(UUID id){return metrics.findBySessionIdOrderByCreatedAtAsc(id);}

    private Summary summarize(List<LlmCallMetric> list){
        long input=0,output=0,cached=0,total=0,latency=0;BigDecimal cost=BigDecimal.ZERO;
        Map<String,Long> callsByModel=new TreeMap<>();
        for(LlmCallMetric m:list){input+=m.getInputTokens();output+=m.getOutputTokens();cached+=m.getCachedTokens();total+=m.getTotalTokens();latency+=m.getLatencyMs();cost=cost.add(m.getEstimatedCostUsd());callsByModel.merge(m.getProvider()+":"+m.getModel(),1L,Long::sum);}
        return new Summary(list.size(),input,output,cached,total,latency,cost.setScale(8,RoundingMode.HALF_UP),Map.copyOf(callsByModel));
    }

    private BigDecimal estimate(String model,LlmUsage usage){
        LlmPricingProperties.ModelPrice p=pricing.models().get(model);if(p==null)return BigDecimal.ZERO.setScale(8);
        BigDecimal input=Objects.requireNonNullElse(p.inputPerMillion(),BigDecimal.ZERO);
        BigDecimal output=Objects.requireNonNullElse(p.outputPerMillion(),BigDecimal.ZERO);
        BigDecimal cached=Objects.requireNonNullElse(p.cachedInputPerMillion(),input);
        long cachedTokens=Math.min(usage.cachedTokens(),usage.inputTokens()); long normalInput=Math.max(0,usage.inputTokens()-cachedTokens);
        BigDecimal cost=input.multiply(BigDecimal.valueOf(normalInput)).divide(MILLION,12,RoundingMode.HALF_UP)
                .add(cached.multiply(BigDecimal.valueOf(cachedTokens)).divide(MILLION,12,RoundingMode.HALF_UP))
                .add(output.multiply(BigDecimal.valueOf(usage.outputTokens())).divide(MILLION,12,RoundingMode.HALF_UP));
        return cost.setScale(8,RoundingMode.HALF_UP);
    }

    public record Summary(long calls,long inputTokens,long outputTokens,long cachedTokens,long totalTokens,long llmLatencyMs,BigDecimal estimatedCostUsd,Map<String,Long> callsByModel){}
}
