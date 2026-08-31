package com.ordevia.aidev.agent.skill;

import com.ordevia.aidev.agent.domain.AgentType;
import java.nio.file.Path;
import java.util.Set;

public record AgentSkill(
        String name,
        String description,
        Set<AgentType> roles,
        Set<String> requiredTools,
        Path source,
        String body
) {}
