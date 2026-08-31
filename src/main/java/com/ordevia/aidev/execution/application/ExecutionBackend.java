package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.ExecutionBackendType;

public interface ExecutionBackend {
    ExecutionBackendType type();
    ExecutionResult execute(ExecutionRequest request);
}
