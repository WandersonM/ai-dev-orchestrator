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
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
}
