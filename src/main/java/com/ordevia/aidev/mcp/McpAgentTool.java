package com.ordevia.aidev.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.tool.AgentTool;
import com.ordevia.aidev.agent.tool.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class McpAgentTool implements AgentTool {
    private final String exposedName;
    private final String serverName;
    private final McpSchema.Tool remoteTool;
    private final McpSyncClient client;
    private final ObjectMapper mapper;

    public McpAgentTool(String serverName, McpSchema.Tool remoteTool, McpSyncClient client, ObjectMapper mapper) {
        this.serverName = serverName;
        this.remoteTool = remoteTool;
        this.client = client;
        this.mapper = mapper;
        this.exposedName = sanitize("mcp_" + serverName + "_" + remoteTool.name());
    }

    @Override public String name() { return exposedName; }

    @Override
    public String description() {
        String description = remoteTool.description() == null ? "" : remoteTool.description();
        return "MCP server '" + serverName + "', remote tool '" + remoteTool.name() + "'. " + description;
    }

    @Override
    public Map<String, Object> inputSchema() {
        try {
            Map<String, Object> schema = mapper.convertValue(remoteTool.inputSchema(), new TypeReference<>() {});
            return schema == null ? genericSchema() : schema;
        } catch (Exception e) {
            return genericSchema();
        }
    }

    @Override
    public ToolResult execute(Path workspace, Map<String, Object> arguments) {
        try {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(remoteTool.name(), arguments));
            String output = mapper.writeValueAsString(result);
            if (Boolean.TRUE.equals(result.isError())) return ToolResult.fail(output);
            return ToolResult.ok(output);
        } catch (Exception e) {
            return ToolResult.fail("MCP tool '" + serverName + "/" + remoteTool.name() + "' failed: " + e.getMessage());
        }
    }

    private Map<String, Object> genericSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("additionalProperties", true);
        return schema;
    }

    private String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_-]", "_");
        return sanitized.length() <= 64 ? sanitized : sanitized.substring(0, 64);
    }
}
