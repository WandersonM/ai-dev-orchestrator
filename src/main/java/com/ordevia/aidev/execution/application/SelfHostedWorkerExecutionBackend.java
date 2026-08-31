package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.ExecutionBackendType;
import com.ordevia.aidev.execution.infrastructure.SelfHostedWorkerProperties;
import com.ordevia.aidev.execution.infrastructure.SelfHostedWorkerProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.UUID;

@Component
public class SelfHostedWorkerExecutionBackend implements ExecutionBackend {
    private final SelfHostedWorkerProperties properties;
    private final RestClient client;
    private final Path localWorkspaceRoot;

    public SelfHostedWorkerExecutionBackend(SelfHostedWorkerProperties properties,
                                            RestClient.Builder builder,
                                            @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.baseUrl()).build();
        this.localWorkspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public ExecutionBackendType type() {
        return ExecutionBackendType.SELF_HOSTED_WORKER;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        if (!properties.clientEnabled()) throw new IllegalStateException("Self-hosted worker client is disabled");
        Path taskRoot = request.taskRoot().toAbsolutePath().normalize();
        Path cwd = request.workingDirectory().toAbsolutePath().normalize();
        if (!taskRoot.startsWith(localWorkspaceRoot)) throw new SecurityException("Task root outside orchestrator workspace root");
        if (!cwd.startsWith(taskRoot)) throw new SecurityException("Working directory outside task root");

        String taskPath = portable(localWorkspaceRoot.relativize(taskRoot));
        String cwdPath = portable(taskRoot.relativize(cwd));
        SelfHostedWorkerProtocol.ExecuteRequest payload = new SelfHostedWorkerProtocol.ExecuteRequest(
                UUID.randomUUID(), taskPath, cwdPath, request.command(), request.timeout().toMillis(), request.environment());

        RestClient.RequestBodySpec spec = client.post().uri("/internal/worker/execute").contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.token())) spec.header("Authorization", "Bearer " + properties.token());
        SelfHostedWorkerProtocol.ExecuteResponse response = spec.body(payload).retrieve().body(SelfHostedWorkerProtocol.ExecuteResponse.class);
        if (response == null || !payload.requestId().equals(response.requestId())) throw new IllegalStateException("Invalid response from self-hosted worker");
        return new ExecutionResult(response.exitCode(), response.output(), type().name());
    }

    private String portable(Path path) {
        String value = path.toString().replace('\\','/');
        return value.equals(".") ? "" : value;
    }
}
