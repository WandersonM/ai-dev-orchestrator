package com.ordevia.aidev.workspace.infrastructure;

import com.ordevia.aidev.workspace.application.CommandPolicy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class LocalCommandExecutor {
    private final CommandPolicy policy;

    public LocalCommandExecutor(CommandPolicy policy) { this.policy = policy; }

    public CommandResult execute(Path workspaceRoot, Path workingDirectory, List<String> command, Duration timeout) {
        return executeInternal(workspaceRoot, workingDirectory, command, timeout, null, true);
    }

    public CommandResult executeIsolated(Path workspaceRoot,
                                         Path workingDirectory,
                                         List<String> command,
                                         Duration timeout,
                                         Map<String, String> environment) {
        return executeInternal(workspaceRoot, workingDirectory, command, timeout, environment, false);
    }

    private CommandResult executeInternal(Path workspaceRoot,
                                          Path workingDirectory,
                                          List<String> command,
                                          Duration timeout,
                                          Map<String, String> environment,
                                          boolean inheritEnvironment) {
        if (command.isEmpty()) throw new IllegalArgumentException("Command cannot be empty");
        policy.validate(command.getFirst());
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path cwd = workingDirectory.toAbsolutePath().normalize();
        if (!cwd.startsWith(root)) throw new SecurityException("Working directory outside workspace root");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
            if (!inheritEnvironment) {
                Map<String, String> target = builder.environment();
                String path = target.get("PATH");
                String home = target.get("HOME");
                String systemRoot = target.get("SystemRoot");
                target.clear();
                if (path != null) target.put("PATH", path);
                if (home != null) target.put("HOME", home);
                if (systemRoot != null) target.put("SystemRoot", systemRoot);
                if (environment != null) target.putAll(environment);
            }
            Process process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to execute command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command interrupted", e);
        }
    }

    public record CommandResult(int exitCode, String output) {}
}
