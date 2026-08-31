package com.ordevia.aidev.llm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class OpenAiLlmGateway implements ProviderLlmGateway {
    private final RestClient client;
    private final String apiKey;
    public OpenAiLlmGateway(RestClient.Builder builder, LlmRoutingProperties properties) { this.client = builder.baseUrl(properties.openai().baseUrl()).build(); this.apiKey = properties.openai().apiKey(); }
    @Override public LlmProvider provider() { return LlmProvider.OPENAI; }
    @Override public LlmResponse execute(LlmRequest request, String model) {
        requireApiKey();
        JsonNode response = client.post().uri("/v1/responses").header("Authorization", "Bearer " + apiKey).contentType(MediaType.APPLICATION_JSON).body(Map.of("model", model, "instructions", request.systemPrompt(), "input", request.userPrompt())).retrieve().body(JsonNode.class);
        String text = extractOutputText(response);
        if (!StringUtils.hasText(text)) throw new IllegalStateException("OpenAI returned an empty text response");
        return new LlmResponse(text, provider(), model);
    }
    private String extractOutputText(JsonNode root) {
        if (root == null) return null;
        StringBuilder out = new StringBuilder();
        for (JsonNode item : root.path("output")) { if (!"message".equals(item.path("type").asText())) continue; for (JsonNode content : item.path("content")) { if ("output_text".equals(content.path("type").asText())) { if (!out.isEmpty()) out.append('\n'); out.append(content.path("text").asText()); } } }
        return out.toString();
    }
    private void requireApiKey() { if (!StringUtils.hasText(apiKey)) throw new IllegalStateException("OPENAI_API_KEY is not configured"); }
}
