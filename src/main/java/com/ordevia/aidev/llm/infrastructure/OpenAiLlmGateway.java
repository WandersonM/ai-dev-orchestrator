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
public class OpenAiLlmGateway implements ProviderLlmGateway {
    private final RestClient client;
    private final String apiKey;
    private final ObjectMapper mapper;

    public OpenAiLlmGateway(RestClient.Builder builder, LlmRoutingProperties properties, ObjectMapper mapper) {
        this.client = builder.baseUrl(properties.openai().baseUrl()).build();
        this.apiKey = properties.openai().apiKey();
        this.mapper = mapper;
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI;
    }

    @Override
    public LlmResponse execute(LlmRequest request, String model) {
        requireApiKey();
        JsonNode response = client.post()
                .uri("/v1/responses")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "instructions", request.systemPrompt(),
                        "input", request.userPrompt()))
                .retrieve()
                .body(JsonNode.class);

        String text = extractOutputText(response);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("OpenAI returned an empty text response");
        }
        return new LlmResponse(text, provider(), model);
    }

    @Override
    public LlmToolResponse executeTools(LlmToolRequest request, String model) {
        requireApiKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", request.systemPrompt());
        body.put("tools", request.tools().stream().map(this::toOpenAiTool).toList());
        body.put("store", true);

        if (StringUtils.hasText(request.previousTurnId())) {
            body.put("previous_response_id", request.previousTurnId());
            body.put("input", request.toolResults().stream().map(result -> Map.of(
                    "type", "function_call_output",
                    "call_id", result.callId(),
                    "output", result.output())).toList());
        } else {
            body.put("input", request.userPrompt());
        }

        JsonNode response = client.post()
                .uri("/v1/responses")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("OpenAI returned an empty response");
        }

        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonNode item : response.path("output")) {
            if (!"function_call".equals(item.path("type").asText())) {
                continue;
            }
            String argumentsJson = item.path("arguments").asText("{}");
            Map<String, Object> arguments;
            try {
                arguments = mapper.readValue(argumentsJson, new TypeReference<>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Invalid OpenAI function arguments for " + item.path("name").asText(), e);
            }
            calls.add(new LlmToolCall(
                    item.path("call_id").asText(item.path("id").asText()),
                    item.path("name").asText(),
                    arguments));
        }

        return new LlmToolResponse(
                extractOutputText(response),
                calls,
                provider(),
                model,
                response.path("id").asText(null));
    }

    private Map<String, Object> toOpenAiTool(LlmToolDefinition tool) {
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
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    if (!out.isEmpty()) out.append('\n');
                    out.append(content.path("text").asText());
                }
            }
        }
        return out.toString();
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }
    }
}
