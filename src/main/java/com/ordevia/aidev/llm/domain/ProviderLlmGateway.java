package com.ordevia.aidev.llm.domain;

public interface ProviderLlmGateway {
    LlmProvider provider();
    LlmResponse execute(LlmRequest request, String model);
}
