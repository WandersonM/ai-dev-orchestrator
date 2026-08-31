package com.ordevia.aidev.llm.infrastructure;

import com.ordevia.aidev.llm.domain.LlmProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aidev.llm")
public record LlmRoutingProperties(Routes routes, OpenAi openai, Gemini gemini) {
    public record Routes(
            Route refinement,
            Route architecture,
            Route backend,
            Route frontend,
            Route qa,
            Route review,
            Route security,
            Route integration,
            Route release,
            Route domainValidation
    ) {}
    public record Route(LlmProvider provider, String model) {}
    public record OpenAi(String apiKey, String baseUrl) {}
    public record Gemini(String apiKey, String baseUrl) {}
}
