package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.ExecutionBackendType;
import com.ordevia.aidev.execution.domain.NetworkPolicy;
import com.ordevia.aidev.workspace.application.CommandPolicy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DockerSandboxExecutionBackend implements ExecutionBackend {
    private final CommandPolicy commandPolicy;

    public DockerSandboxExecutionBackend(CommandPolicy commandPolicy) {
        this.commandPolicy = commandPolicy;
    }

    @Override
    public ExecutionBackendType type() {
        return ExecutionBackendType.DOCKER;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        if (request.command().isEmpty()) throw new IllegalArgumentException("Command cannot be empty");
        commandPolicy.validate(request.command().getFirst());
        if (!StringUtils.hasText(request.profile().getContainerImage())) {
            throw new IllegalStateException("Docker environment profile requires containerImage");
        }

        Path root = request.taskRoot().toAbsolutePath().normalize();
        Path cwd = request.workingDirectory().toAbsolutePath().normalize();
        if (!cwd.startsWith(root)) throw new SecurityException("Docker working directory outside task root");
        String relative = root.relativize(cwd).toString().replace('\\', '/');
        String containerCwd = relative.isBlank() ? "/workspace" : "/workspace/" + relative;
        String containerName = "aidev-" + UUID.randomUUID().toString().substring(0, 12);

        List<String> docker = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--name", containerName,
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges:true",
                "--pids-limit", String.valueOf(request.profile().getPidsLimit()),
                "--memory", request.profile().getMemoryLimitMb() + "m",
                "--cpus", String.valueOf(request.profile().getCpuLimit()),
                "-v", root + ":/workspace:rw",
                "-w", containerCwd
        ));
        if (request.profile().getNetworkPolicy() == NetworkPolicy.DENY) {
            docker.addAll(List.of("--network", "none"));
        }
        for (String name : request.environment().keySet()) {
            docker.addAll(List.of("--env", name));
        }
        docker.add(request.profile().getContainerImage());
        docker.addAll(request.command());

        ProcessBuilder builder = new ProcessBuilder(docker).redirectErrorStream(true);
        Map<String, String> processEnvironment = builder.environment();
        request.environment().forEach(processEnvironment::put);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                forceRemove(containerName);
                throw new IllegalStateException("Docker command timed out after " + request.timeout());
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new ExecutionResult(process.exitValue(), output, type().name());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            forceRemove(containerName);
            throw new IllegalStateException("Docker execution interrupted", e);
        } catch (Exception e) {
            forceRemove(containerName);
            if (e instanceof IllegalStateException ise) throw ise;
            throw new IllegalStateException("Unable to execute Docker sandbox", e);
        }
    }

    private void forceRemove(String containerName) {
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}
