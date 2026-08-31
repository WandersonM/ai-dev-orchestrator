package com.ordevia.aidev.llm.domain;

public record LlmRequest(LlmTask task, String systemPrompt, String userPrompt) {}
