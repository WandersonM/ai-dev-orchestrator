package com.ordevia.aidev.agent.tool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public interface AgentTool {
    String name();
    String description();

    default Map<String, Object> inputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("additionalProperties", true);
        return schema;
    }

    ToolResult execute(Path workspace, Map<String, Object> arguments);
}
