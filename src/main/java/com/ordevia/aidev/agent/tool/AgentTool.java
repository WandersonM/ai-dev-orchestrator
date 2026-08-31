package com.ordevia.aidev.agent.tool;

import java.nio.file.Path;
import java.util.Map;

public interface AgentTool {
    String name();
    String description();
    ToolResult execute(Path workspace, Map<String, Object> arguments);
}
