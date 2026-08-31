package com.ordevia.aidev.agent.domain;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public record AgentContext(UUID workItemId, Path repository, String branch, String title, String description, String specification, Map<String, Object> metadata) {}
