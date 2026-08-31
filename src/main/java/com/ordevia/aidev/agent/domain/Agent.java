package com.ordevia.aidev.agent.domain;

public interface Agent {
    AgentType type();
    AgentResult execute(AgentContext context);
}
