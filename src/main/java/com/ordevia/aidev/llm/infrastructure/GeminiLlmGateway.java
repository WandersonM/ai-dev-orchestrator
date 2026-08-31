package com.ordevia.aidev.llm.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GeminiLlmGateway implements ProviderLlmGateway {
    private final RestClient client;
    private final String apiKey;
    public GeminiLlmGateway(RestClient.Builder builder, LlmRoutingProperties properties) { this.client = builder.baseUrl(properties.gemini().baseUrl()).build(); this.apiKey = properties.gemini().apiKey(); }
    @Override public LlmProvider provider() { return LlmProvider.GEMINI; }
    @Override public LlmResponse execute(LlmRequest request, String model) {
        requireApiKey();
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", model); body.put("system_instruction", request.systemPrompt()); body.put("input", request.userPrompt()); body.put("store", false);
        JsonNode response = client.post().uri("/v1beta/interactions").header("x-goog-api-key", apiKey).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        String text = extractOutputText(response);
        if (!StringUtils.hasText(text)) throw new IllegalStateException("Gemini returned an empty text response");
        return new LlmResponse(text, provider(), model);
    }
    private String extractOutputText(JsonNode root) {
        if (root == null) return null;
        StringBuilder out = new StringBuilder();
        for (JsonNode step : root.path("steps")) { if (!"model_output".equals(step.path("type").asText())) continue; for (JsonNode content : step.path("content")) { if ("text".equals(content.path("type").asText())) { if (!out.isEmpty()) out.append('\n'); out.append(content.path("text").asText()); } } }
        return out.toString();
    }
    private void requireApiKey() { if (!StringUtils.hasText(apiKey)) throw new IllegalStateException("GEMINI_API_KEY is not configured"); }
}
