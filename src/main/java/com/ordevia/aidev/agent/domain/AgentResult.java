package com.ordevia.aidev.agent.domain;

public record AgentResult(boolean success, String output, String error) {
    public static AgentResult success(String output) { return new AgentResult(true, output, null); }
    public static AgentResult failure(String error) { return new AgentResult(false, null, error); }
}
