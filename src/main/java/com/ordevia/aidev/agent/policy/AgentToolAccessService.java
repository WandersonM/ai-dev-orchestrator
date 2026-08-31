package com.ordevia.aidev.agent.policy;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.agent.tool.AgentTool;
import com.ordevia.aidev.agent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentToolAccessService {
    private final ToolRegistry registry;
    private final ToolPolicy policy;

    public AgentToolAccessService(ToolRegistry registry, ToolPolicy policy) {
        this.registry = registry;
        this.policy = policy;
    }

    public List<AgentTool> allowedTools(AgentType agentType) {
        return registry.all().stream()
                .filter(tool -> policy.isAllowed(agentType, tool.name()))
                .toList();
    }

    public AgentTool required(AgentType agentType, String toolName) {
        policy.requireAllowed(agentType, toolName);
        return registry.required(toolName);
    }
}
