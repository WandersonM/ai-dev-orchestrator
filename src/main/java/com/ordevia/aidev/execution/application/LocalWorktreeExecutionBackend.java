package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.ExecutionBackendType;
import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import org.springframework.stereotype.Component;

@Component
public class LocalWorktreeExecutionBackend implements ExecutionBackend {
    private final LocalCommandExecutor commands;

    public LocalWorktreeExecutionBackend(LocalCommandExecutor commands) {
        this.commands = commands;
    }

    @Override
    public ExecutionBackendType type() {
        return ExecutionBackendType.LOCAL_WORKTREE;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        var result = commands.executeIsolated(
                request.taskRoot(),
                request.workingDirectory(),
                request.command(),
                request.timeout(),
                request.environment());
        return new ExecutionResult(result.exitCode(), result.output(), type().name());
    }
}
