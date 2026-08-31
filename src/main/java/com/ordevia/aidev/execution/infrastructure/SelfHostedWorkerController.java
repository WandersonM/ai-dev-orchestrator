package com.ordevia.aidev.execution.infrastructure;

import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/internal/worker")
@ConditionalOnProperty(prefix="aidev.execution.self-hosted-worker", name="server-enabled", havingValue="true")
public class SelfHostedWorkerController {
    private static final long MAX_TIMEOUT_MS = Duration.ofHours(1).toMillis();
    private static final int MAX_CACHE = 2_000;

    private final SelfHostedWorkerProperties properties;
    private final LocalCommandExecutor commands;
    private final Path workspaceRoot;
    private final Map<UUID, SelfHostedWorkerProtocol.ExecuteResponse> completed = new ConcurrentHashMap<>();

    public SelfHostedWorkerController(SelfHostedWorkerProperties properties, LocalCommandExecutor commands) {
        this.properties = properties;
        this.commands = commands;
        this.workspaceRoot = Path.of(properties.workspaceRoot()).toAbsolutePath().normalize();
    }

    @GetMapping("/health")
    public SelfHostedWorkerProtocol.Health health(@RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization) {
        authorize(authorization);
        return new SelfHostedWorkerProtocol.Health("UP");
    }

    @PostMapping("/execute")
    public SelfHostedWorkerProtocol.ExecuteResponse execute(
            @RequestHeader(value=HttpHeaders.AUTHORIZATION, required=false) String authorization,
            @RequestBody SelfHostedWorkerProtocol.ExecuteRequest request) {
        authorize(authorization);
        if (request.requestId() == null) throw new IllegalArgumentException("requestId is required");
        SelfHostedWorkerProtocol.ExecuteResponse previous = completed.get(request.requestId());
        if (previous != null) return previous;
        if (request.command() == null || request.command().isEmpty()) throw new IllegalArgumentException("command is required");
        long timeout = Math.min(Math.max(request.timeoutMillis(), 1_000), MAX_TIMEOUT_MS);

        Path taskRoot = resolveUnder(workspaceRoot, request.taskPath());
        Path cwd = resolveUnder(taskRoot, request.workingDirectory());
        LocalCommandExecutor.CommandResult result = commands.executeIsolated(
                taskRoot, cwd, request.command(), Duration.ofMillis(timeout), request.environment());
        SelfHostedWorkerProtocol.ExecuteResponse response = new SelfHostedWorkerProtocol.ExecuteResponse(
                request.requestId(), result.exitCode(), result.output());
        if (completed.size() >= MAX_CACHE) completed.clear();
        completed.put(request.requestId(), response);
        return response;
    }

    private Path resolveUnder(Path root, String relative) {
        Path resolved = root.resolve(relative == null ? "" : relative).normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("Worker path escapes configured workspace root");
        return resolved;
    }

    private void authorize(String authorization) {
        if (properties.token() == null || properties.token().isBlank()) return;
        String expected = "Bearer " + properties.token();
        if (authorization == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid worker authorization");
        }
    }
}
