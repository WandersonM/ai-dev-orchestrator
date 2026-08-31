package com.ordevia.aidev.llm.domain;

public record LlmResponse(String content, LlmProvider provider, String model, LlmUsage usage, long latencyMs) {
    public LlmResponse(String content, LlmProvider provider, String model) {
        this(content, provider, model, LlmUsage.empty(), 0);
    }
}
