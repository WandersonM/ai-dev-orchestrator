package com.ordevia.aidev.execution.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SelfHostedWorkerProtocol {
    private SelfHostedWorkerProtocol() {}

    public record ExecuteRequest(
            UUID requestId,
            String taskPath,
            String workingDirectory,
            List<String> command,
            long timeoutMillis,
            Map<String,String> environment
    ) {}

    public record ExecuteResponse(UUID requestId, int exitCode, String output) {}

    public record Health(String status) {}
}
