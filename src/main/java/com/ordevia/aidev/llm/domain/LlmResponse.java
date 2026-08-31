package com.ordevia.aidev.llm.domain;

public record LlmResponse(String content, LlmProvider provider, String model) {}
