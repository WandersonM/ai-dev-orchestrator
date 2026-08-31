package com.ordevia.aidev.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        tools.forEach(this::register);
    }

    public void register(AgentTool tool) {
        AgentTool previous = tools.putIfAbsent(tool.name(), tool);
        if (previous != null && previous != tool) {
            throw new IllegalStateException("Tool already registered: " + tool.name());
        }
    }

    public void unregister(String name) {
        tools.remove(name);
    }

    public AgentTool required(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("Unknown tool: " + name);
        return tool;
    }

    public Collection<AgentTool> all() {
        return List.copyOf(tools.values());
    }
}
