package com.ordevia.aidev.llm.domain;

public record LlmToolResult(
        String callId,
        String name,
        String output
) {}
