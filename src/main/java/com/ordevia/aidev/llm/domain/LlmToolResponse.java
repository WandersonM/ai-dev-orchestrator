package com.ordevia.aidev.llm.domain;

import java.util.List;

public record LlmToolResponse(
        String text,
        List<LlmToolCall> toolCalls,
        LlmProvider provider,
        String model,
        String turnId
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
