package com.ordevia.aidev.execution.application;

public record ExecutionResult(int exitCode, String output, String backend) {
    public boolean success() { return exitCode == 0; }
}
