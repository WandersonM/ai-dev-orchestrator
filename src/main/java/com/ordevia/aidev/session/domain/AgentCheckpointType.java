package com.ordevia.aidev.session.domain;

public enum AgentCheckpointType {
    SESSION_STARTED,
    BEFORE_LLM,
    AFTER_LLM,
    AFTER_TOOL,
    HUMAN_MESSAGE_APPLIED,
    PAUSED,
    RESUMED,
    CANCELLED,
    COMPLETED,
    FAILED
}
