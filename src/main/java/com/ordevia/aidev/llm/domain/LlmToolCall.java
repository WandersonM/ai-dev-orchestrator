package com.ordevia.aidev.llm.domain;

import java.util.Map;

public record LlmToolCall(
        String id,
        String name,
        Map<String, Object> arguments
) {}
