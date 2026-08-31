package com.ordevia.aidev.session.domain;

public enum AgentSessionStatus {
    CREATED,
    RUNNING,
    PAUSE_REQUESTED,
    PAUSED,
    CANCEL_REQUESTED,
    CANCELLED,
    COMPLETED,
    FAILED;

    public boolean terminal() {
        return this == CANCELLED || this == COMPLETED || this == FAILED;
    }

    public boolean active() {
        return !terminal();
    }
}
