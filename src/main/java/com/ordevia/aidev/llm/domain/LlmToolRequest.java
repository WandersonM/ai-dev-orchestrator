package com.ordevia.aidev.llm.domain;

import java.util.List;

public record LlmToolRequest(
        LlmTask task,
        String systemPrompt,
        String userPrompt,
        List<LlmToolDefinition> tools,
        String previousTurnId,
        List<LlmToolResult> toolResults
) {
    public static LlmToolRequest initial(LlmTask task, String systemPrompt, String userPrompt, List<LlmToolDefinition> tools) {
        return new LlmToolRequest(task, systemPrompt, userPrompt, tools, null, List.of());
    }

    public LlmToolRequest continueWith(String previousTurnId, List<LlmToolResult> toolResults) {
        return new LlmToolRequest(task, systemPrompt, userPrompt, tools, previousTurnId, toolResults);
    }
}
