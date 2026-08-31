package com.ordevia.aidev.planning.domain;

public enum PlanningStatus {
    DISCOVERING,
    WAITING_FOR_USER_INPUT,
    READY_FOR_REVIEW,
    APPROVED,
    HUMAN_REQUIRED,
    FAILED
}
