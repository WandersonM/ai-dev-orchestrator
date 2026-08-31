package com.ordevia.aidev.llm.domain;

import java.util.List;

public record LlmToolResponse(
        String text,
        List<LlmToolCall> toolCalls,
        LlmProvider provider,
        String model,
        String turnId,
        LlmUsage usage,
        long latencyMs
) {
    public LlmToolResponse(String text,List<LlmToolCall> toolCalls,LlmProvider provider,String model,String turnId) {
        this(text,toolCalls,provider,model,turnId,LlmUsage.empty(),0);
    }
    public boolean hasToolCalls(){return toolCalls!=null&&!toolCalls.isEmpty();}
}
