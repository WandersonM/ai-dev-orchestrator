package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.EnvironmentProfile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ExecutionRequest(
        Path taskRoot,
        Path workingDirectory,
        List<String> command,
        Duration timeout,
        EnvironmentProfile profile,
        Map<String, String> environment
) {}
