package com.ordevia.aidev.workspace.infrastructure;

import com.ordevia.aidev.workspace.application.CommandPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LocalCommandExecutor {
    private final CommandPolicy policy;
    private final int maxCapturedOutputBytes;

    public LocalCommandExecutor(CommandPolicy policy,
                                @Value("${aidev.execution.max-captured-output-bytes:2097152}") int maxCapturedOutputBytes) {
        this.policy = policy;
        this.maxCapturedOutputBytes = Math.max(64 * 1024, maxCapturedOutputBytes);
    }

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
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
            if (!inheritEnvironment) isolateEnvironment(builder, environment);
            process = builder.start();

            ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(maxCapturedOutputBytes, 64 * 1024));
            AtomicLong totalBytes = new AtomicLong();
            AtomicReference<IOException> readFailure = new AtomicReference<>();
            Process running = process;
            Thread drainer = Thread.ofVirtual().name("aidev-command-output").start(() -> drain(running.getInputStream(), captured, totalBytes, readFailure));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                joinDrainer(drainer);
                throw new IllegalStateException("Command timed out after " + timeout);
            }
            joinDrainer(drainer);
            if (readFailure.get() != null) throw new IllegalStateException("Unable to read command output", readFailure.get());
            String output = captured.toString(StandardCharsets.UTF_8);
            if (totalBytes.get() > maxCapturedOutputBytes) {
                output += "\n...[output truncated by orchestrator: captured " + maxCapturedOutputBytes + " of " + totalBytes.get() + " bytes]";
            }
            return new CommandResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to execute command", e);
        } catch (InterruptedException e) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command interrupted", e);
        }
    }

    private void isolateEnvironment(ProcessBuilder builder, Map<String,String> environment) {
        Map<String, String> target = builder.environment();
        String path = target.get("PATH");
        String home = target.get("HOME");
        String systemRoot = target.get("SystemRoot");
        String tmp = target.get("TMPDIR");
        target.clear();
        if (path != null) target.put("PATH", path);
        if (home != null) target.put("HOME", home);
        if (systemRoot != null) target.put("SystemRoot", systemRoot);
        if (tmp != null) target.put("TMPDIR", tmp);
        if (environment != null) target.putAll(environment);
    }

    private void drain(InputStream input, ByteArrayOutputStream captured, AtomicLong total, AtomicReference<IOException> failure) {
        byte[] buffer = new byte[16 * 1024];
        try (input) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total.addAndGet(read);
                int remaining = maxCapturedOutputBytes - captured.size();
                if (remaining > 0) captured.write(buffer, 0, Math.min(read, remaining));
            }
        } catch (IOException e) {
            failure.set(e);
        }
    }

    private void joinDrainer(Thread drainer) throws InterruptedException {
        drainer.join(Duration.ofSeconds(10));
        if (drainer.isAlive()) throw new IllegalStateException("Command output drainer did not terminate");
    }

    public record CommandResult(int exitCode, String output) {}
}
