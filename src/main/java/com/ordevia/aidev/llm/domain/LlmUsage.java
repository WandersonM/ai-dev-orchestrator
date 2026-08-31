package com.ordevia.aidev.llm.domain;

public record LlmUsage(long inputTokens,long outputTokens,long cachedTokens,long totalTokens) {
    public static LlmUsage empty(){return new LlmUsage(0,0,0,0);}
}
