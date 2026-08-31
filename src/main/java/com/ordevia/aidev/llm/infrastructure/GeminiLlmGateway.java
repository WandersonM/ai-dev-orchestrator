package com.ordevia.aidev.llm.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
public class GeminiLlmGateway implements ProviderLlmGateway {
    private final RestClient client;
    private final String apiKey;
    private final ObjectMapper mapper;

    public GeminiLlmGateway(RestClient.Builder builder, LlmRoutingProperties properties, ObjectMapper mapper) {
        this.client = builder.baseUrl(properties.gemini().baseUrl()).build();
        this.apiKey = properties.gemini().apiKey();
        this.mapper = mapper;
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.GEMINI;
    }

    @Override
    public LlmResponse execute(LlmRequest request, String model) {
        requireApiKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("system_instruction", request.systemPrompt());
        body.put("input", request.userPrompt());
        body.put("store", false);

        JsonNode response = client.post()
                .uri("/v1beta/interactions")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String text = extractOutputText(response);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("Gemini returned an empty text response");
        }
        return new LlmResponse(text, provider(), model);
    }

    @Override
    public LlmToolResponse executeTools(LlmToolRequest request, String model) {
        requireApiKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("system_instruction", request.systemPrompt());
        body.put("tools", request.tools().stream().map(this::toGeminiTool).toList());
        body.put("store", true);

        if (StringUtils.hasText(request.previousTurnId())) {
            body.put("previous_interaction_id", request.previousTurnId());
            body.put("input", request.toolResults().stream().map(result -> Map.of(
                    "type", "function_result",
                    "name", result.name(),
                    "call_id", result.callId(),
                    "result", List.of(Map.of("type", "text", "text", result.output())))).toList());
        } else {
            body.put("input", request.userPrompt());
        }

        JsonNode response = client.post()
                .uri("/v1beta/interactions")
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Gemini returned an empty response");
        }

        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonNode step : response.path("steps")) {
            if (!"function_call".equals(step.path("type").asText())) {
                continue;
            }
            Map<String, Object> arguments = mapper.convertValue(
                    step.path("arguments"),
                    new TypeReference<>() {});
            calls.add(new LlmToolCall(
                    step.path("id").asText(),
                    step.path("name").asText(),
                    arguments));
        }

        return new LlmToolResponse(
                extractOutputText(response),
                calls,
                provider(),
                model,
                response.path("id").asText(null));
    }

    private Map<String, Object> toGeminiTool(LlmToolDefinition tool) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "function");
        value.put("name", tool.name());
        value.put("description", tool.description());
        value.put("parameters", tool.parameters());
        return value;
    }

    private String extractOutputText(JsonNode root) {
        if (root == null) return null;
        StringBuilder out = new StringBuilder();
        for (JsonNode step : root.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) continue;
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asText())) {
                    if (!out.isEmpty()) out.append('\n');
                    out.append(content.path("text").asText());
                }
            }
        }
        return out.toString();
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }
    }
}
