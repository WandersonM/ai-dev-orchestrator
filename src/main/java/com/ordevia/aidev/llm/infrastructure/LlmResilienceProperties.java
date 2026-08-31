package com.ordevia.aidev.llm.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "aidev.llm.resilience")
public record LlmResilienceProperties(
        boolean enabled,
        int failureThreshold,
        Duration openDuration,
        String fallbackOpenaiModel,
        String fallbackGeminiModel
) {
    public LlmResilienceProperties {
        if (failureThreshold <= 0) failureThreshold = 3;
        if (openDuration == null) openDuration = Duration.ofSeconds(45);
        if (fallbackOpenaiModel == null || fallbackOpenaiModel.isBlank()) fallbackOpenaiModel = "gpt-5.6-sol";
        if (fallbackGeminiModel == null || fallbackGeminiModel.isBlank()) fallbackGeminiModel = "gemini-3.7-flash";
    }
}
