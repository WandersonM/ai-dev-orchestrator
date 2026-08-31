package com.ordevia.aidev.agent.policy;

import com.ordevia.aidev.agent.domain.AgentType;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;

import java.util.List;

@Component
public class ToolPolicy {
    private final ToolPolicyProperties properties;

    public ToolPolicy(ToolPolicyProperties properties) {
        this.properties = properties;
    }

    public boolean isAllowed(AgentType agentType, String toolName) {
        ToolPolicyProperties.Rule rule = properties.getPolicies().get(agentType);
        if (rule == null) return properties.getDefaultEffect() == ToolPolicyProperties.Effect.ALLOW;

        if (matches(rule.getDeny(), toolName)) return false;
        if (matches(rule.getAllow(), toolName)) return true;

        return properties.getDefaultEffect() == ToolPolicyProperties.Effect.ALLOW;
    }

    public void requireAllowed(AgentType agentType, String toolName) {
        if (!isAllowed(agentType, toolName)) {
            throw new SecurityException("Tool '" + toolName + "' is not allowed for agent " + agentType);
        }
    }

    private boolean matches(List<String> patterns, String toolName) {
        if (patterns == null || patterns.isEmpty()) return false;
        for (String pattern : patterns) {
            if (pattern != null && PatternMatchUtils.simpleMatch(pattern.trim(), toolName)) return true;
        }
        return false;
    }
}
