package com.ordevia.aidev.agent.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void shouldRegisterAndUnregisterDynamicTool() {
        ToolRegistry registry = new ToolRegistry(List.of());
        AgentTool tool = tool("mcp_docs_search");

        registry.register(tool);
        assertSame(tool, registry.required("mcp_docs_search"));

        registry.unregister("mcp_docs_search");
        assertThrows(IllegalArgumentException.class, () -> registry.required("mcp_docs_search"));
    }

    @Test
    void shouldRejectNameCollision() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("same_name")));
        assertThrows(IllegalStateException.class, () -> registry.register(tool("same_name")));
    }

    private AgentTool tool(String name) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "test"; }
            @Override public ToolResult execute(Path workspace, Map<String, Object> arguments) { return ToolResult.ok("ok"); }
        };
    }
}
