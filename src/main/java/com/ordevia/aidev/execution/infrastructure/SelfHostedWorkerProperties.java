package com.ordevia.aidev.execution.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aidev.execution.self-hosted-worker")
public record SelfHostedWorkerProperties(
        boolean clientEnabled,
        boolean serverEnabled,
        String baseUrl,
        String token,
        String workspaceRoot
) {
    public SelfHostedWorkerProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:8090";
        if (workspaceRoot == null || workspaceRoot.isBlank()) workspaceRoot = "./workspace";
    }
}
