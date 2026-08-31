package com.ordevia.aidev.agent.tool;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        tools.forEach(tool -> this.tools.put(tool.name(), tool));
    }

    public AgentTool required(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("Unknown tool: " + name);
        return tool;
    }

    public Collection<AgentTool> all() { return Collections.unmodifiableCollection(tools.values()); }
}
