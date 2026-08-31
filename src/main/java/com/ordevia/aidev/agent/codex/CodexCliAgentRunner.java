package com.ordevia.aidev.agent.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.domain.AgentContext;
import com.ordevia.aidev.agent.domain.AgentResult;
import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.audit.application.AuditService;
import com.ordevia.aidev.security.SecretRedactor;
import com.ordevia.aidev.session.application.AgentSessionService;
import com.ordevia.aidev.session.domain.AgentCheckpointType;
import com.ordevia.aidev.session.domain.AgentSessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
public class CodexCliAgentRunner {
    private static final Logger log = LoggerFactory.getLogger(CodexCliAgentRunner.class);
    private static final int MAX_CAPTURE_BYTES = 2 * 1024 * 1024;

    private final CodexCliProperties properties;
    private final AgentSessionService sessions;
    private final AuditService audit;
    private final SecretRedactor redactor;
    private final ObjectMapper mapper;

    public CodexCliAgentRunner(CodexCliProperties properties,
                               AgentSessionService sessions,
                               AuditService audit,
                               SecretRedactor redactor,
                               ObjectMapper mapper) {
        this.properties = properties;
        this.sessions = sessions;
        this.audit = audit;
        this.redactor = redactor;
        this.mapper = mapper;
    }

    public boolean supports(AgentType role) {
        return properties.isEnabled() && properties.getRoles().contains(role);
    }

    public AgentResult run(AgentType role, AgentContext context, String systemPrompt, String userPrompt) {
        var session = sessions.openOrResume(context.workItemId(), role);
        int step = session.getCurrentStep();
        Path lastMessage = null;
        try {
            WorkspaceRoots roots = resolveRoots(context.repository());
            lastMessage = Files.createTempFile("aidev-codex-last-", ".md");
            String prompt = buildPrompt(role, context, systemPrompt, userPrompt);
            List<String> command = command(role, roots, lastMessage);

            var before = sessions.controlPoint(session.getId(), step, AgentCheckpointType.BEFORE_LLM,
                    "Delegating turn to Codex CLI", null);
            if (before.status() == AgentSessionStatus.PAUSED) sessions.awaitResumeOrCancel(session.getId());
            if (before.status() == AgentSessionStatus.CANCELLED) throw new AgentSessionService.AgentSessionCancelledException("Codex session cancelled before start");

            audit.append(context.workItemId(), session.getId(), "CODEX_CLI_STARTED", "AGENT", role.name(),
                    "AgentSession", session.getId().toString(), Map.of(
                            "binary", properties.getBinary(),
                            "sandbox", sandboxFor(role),
                            "ephemeral", properties.isEphemeral(),
                            "root", roots.primary().toString(),
                            "additionalRoots", roots.additional().size(),
                            "billingMode", "CHATGPT_SUBSCRIPTION"));

            Instant started = Instant.now();
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(roots.primary().toFile())
                    .redirectErrorStream(true);
            sanitizeEnvironment(builder.environment());
            Process process = builder.start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            }

            Future<String> outputFuture;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                outputFuture = executor.submit(() -> readBounded(process.getInputStream(), MAX_CAPTURE_BYTES));
                long deadline = System.nanoTime() + properties.getTimeout().toNanos();
                boolean finished = false;
                while (System.nanoTime() < deadline) {
                    if (process.waitFor(500, TimeUnit.MILLISECONDS)) { finished = true; break; }
                    var state = sessions.get(session.getId()).getStatus();
                    if (state == AgentSessionStatus.CANCEL_REQUESTED || state == AgentSessionStatus.CANCELLED) {
                        process.destroy();
                        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
                        sessions.controlPoint(session.getId(), step, AgentCheckpointType.CANCELLED, "Codex CLI process cancelled", null);
                        throw new AgentSessionService.AgentSessionCancelledException("Codex CLI execution cancelled");
                    }
                }
                if (!finished) {
                    process.destroy();
                    if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
                    throw new IllegalStateException("Codex CLI timed out after " + properties.getTimeout());
                }
                String captured = redactor.redact(outputFuture.get(10, TimeUnit.SECONDS));
                int exit = process.exitValue();
                String finalMessage = Files.exists(lastMessage) ? redactor.redact(Files.readString(lastMessage)) : "";
                if (!StringUtils.hasText(finalMessage)) finalMessage = extractFinalMessage(captured);
                long durationMs = Duration.between(started, Instant.now()).toMillis();

                Map<String, Object> summary = summarizeJsonl(captured);
                summary.put("exitCode", exit);
                summary.put("durationMs", durationMs);
                summary.put("sandbox", sandboxFor(role));
                audit.append(context.workItemId(), session.getId(), exit == 0 ? "CODEX_CLI_COMPLETED" : "CODEX_CLI_FAILED",
                        "AGENT", role.name(), "AgentSession", session.getId().toString(), summary);

                sessions.controlPoint(session.getId(), step, AgentCheckpointType.AFTER_LLM,
                        trim(StringUtils.hasText(finalMessage) ? finalMessage : captured, 8000), null);
                if (exit != 0) {
                    String error = "Codex CLI exited with code " + exit + ": " + trim(captured, 6000);
                    sessions.fail(session.getId(), step, error);
                    return AgentResult.failure(error);
                }
                sessions.complete(session.getId(), step, trim(finalMessage, 8000));
                return AgentResult.success(finalMessage);
            }
        } catch (AgentSessionService.AgentSessionCancelledException e) {
            return AgentResult.failure("SESSION_CANCELLED: " + e.getMessage());
        } catch (Exception e) {
            String error = redactor.redact(Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
            log.warn("Codex CLI execution failed for {}: {}", role, error);
            sessions.fail(session.getId(), step, error);
            audit.append(context.workItemId(), session.getId(), "CODEX_CLI_FAILED", "AGENT", role.name(),
                    "AgentSession", session.getId().toString(), Map.of("error", trim(error, 2000)));
            return AgentResult.failure(error);
        } finally {
            if (lastMessage != null) try { Files.deleteIfExists(lastMessage); } catch (Exception ignored) {}
        }
    }

    public Status status() {
        try {
            ProcessResult version = runProbe(List.of(properties.getBinary(), "--version"), Duration.ofSeconds(10));
            ProcessResult login = runProbe(List.of(properties.getBinary(), "login", "status"), Duration.ofSeconds(15));
            boolean loggedIn = login.exitCode() == 0 && login.output().toLowerCase().contains("logged in");
            return new Status(true, properties.isEnabled(), version.output().trim(), loggedIn,
                    login.output().trim(), List.copyOf(properties.getRoles()));
        } catch (Exception e) {
            return new Status(false, properties.isEnabled(), null, false,
                    redactor.redact(Objects.toString(e.getMessage(), "Codex CLI unavailable")), List.copyOf(properties.getRoles()));
        }
    }

    private List<String> command(AgentType role, WorkspaceRoots roots, Path lastMessage) {
        List<String> args = new ArrayList<>();
        args.add(properties.getBinary());
        args.add("exec");
        args.add("--json");
        args.add("--color"); args.add("never");
        args.add("--sandbox"); args.add(sandboxFor(role));
        args.add("-c"); args.add("approval_policy=\"never\"");
        args.add("--output-last-message"); args.add(lastMessage.toString());
        args.add("-C"); args.add(roots.primary().toString());
        for (Path additional : roots.additional()) { args.add("--add-dir"); args.add(additional.toString()); }
        if (properties.isEphemeral()) args.add("--ephemeral");
        if (properties.isIgnoreUserConfig()) args.add("--ignore-user-config");
        if (StringUtils.hasText(properties.getModel())) { args.add("--model"); args.add(properties.getModel()); }
        args.add("-");
        return args;
    }

    private String sandboxFor(AgentType role) {
        return switch (role) {
            case BACKEND_DEVELOPER, FRONTEND_DEVELOPER, QA_ENGINEER, INTEGRATION_ENGINEER -> "workspace-write";
            default -> "read-only";
        };
    }

    private String buildPrompt(AgentType role, AgentContext context, String systemPrompt, String userPrompt) {
        boolean writable = "workspace-write".equals(sandboxFor(role));
        return """
                You are being delegated a task by AI Dev Orchestrator.
                Your role is %s.

                GOVERNANCE:
                - Work only inside the supplied repository/workspace roots.
                - Do not push, merge, publish releases, change remote branches, or access production.
                - Do not modify .git internals.
                - Respect AGENTS.md and repository-local instructions.
                - Do not invent business rules. The approved specification is authoritative.
                - Leave all code changes uncommitted; the orchestrator owns commit and PR publication.
                - Network access is not available in the Codex workspace sandbox unless separately configured outside this integration.
                - This role is %s. %s
                - End with a concise markdown report covering actions, changed files, verification and remaining risks.

                ROLE INSTRUCTIONS:
                %s

                TASK:
                %s

                WORK ITEM:
                %s — %s
                """.formatted(role.name(), writable ? "WRITE-CAPABLE" : "READ-ONLY",
                writable ? "You may edit files and run repository-local verification commands." : "Do not edit files; inspect and report only.",
                systemPrompt, userPrompt, context.workItemId(), context.title());
    }

    private WorkspaceRoots resolveRoots(Path taskRoot) throws Exception {
        Path root = taskRoot.toAbsolutePath().normalize();
        if (Files.exists(root.resolve(".git"))) return new WorkspaceRoots(root, List.of());
        List<Path> repositories;
        try (var children = Files.list(root)) {
            repositories = children.filter(Files::isDirectory).filter(p -> Files.exists(p.resolve(".git"))).sorted().toList();
        }
        if (repositories.isEmpty()) throw new IllegalStateException("Codex CLI requires a Git repository workspace: " + root);
        return new WorkspaceRoots(repositories.getFirst(), repositories.subList(1, repositories.size()));
    }

    private void sanitizeEnvironment(Map<String, String> env) {
        if (!properties.isStripApiKeyEnvironment()) return;
        env.remove("OPENAI_API_KEY");
        env.remove("CODEX_API_KEY");
    }

    private ProcessResult runProbe(List<String> command, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        sanitizeEnvironment(builder.environment());
        Process process = builder.start();
        String output;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> readBounded(process.getInputStream(), 256 * 1024));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Command timed out: " + command.getFirst());
            }
            output = redactor.redact(future.get(5, TimeUnit.SECONDS));
        }
        return new ProcessResult(process.exitValue(), output);
    }

    private String readBounded(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream kept = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int read;
        int remaining = maxBytes;
        boolean truncated = false;
        while ((read = in.read(buffer)) >= 0) {
            if (remaining > 0) {
                int take = Math.min(read, remaining);
                kept.write(buffer, 0, take);
                remaining -= take;
                if (take < read) truncated = true;
            } else truncated = true;
        }
        String value = kept.toString(StandardCharsets.UTF_8);
        return truncated ? value + "\n...[Codex output truncated by orchestrator]" : value;
    }

    private String extractFinalMessage(String jsonl) {
        String last = "";
        for (String line : jsonl.split("\\R")) {
            try {
                JsonNode node = mapper.readTree(line);
                if ("item.completed".equals(node.path("type").asText())) {
                    JsonNode item = node.path("item");
                    if ("agent_message".equals(item.path("type").asText()) && item.hasNonNull("text")) last = item.path("text").asText();
                }
            } catch (Exception ignored) {}
        }
        return last;
    }

    private Map<String, Object> summarizeJsonl(String jsonl) {
        long input = 0, output = 0, cached = 0;
        String threadId = null;
        int commands = 0;
        for (String line : jsonl.split("\\R")) {
            try {
                JsonNode node = mapper.readTree(line);
                String type = node.path("type").asText();
                if ("thread.started".equals(type)) threadId = node.path("thread_id").asText(null);
                if (type.contains("command") || "command_execution".equals(node.path("item").path("type").asText())) commands++;
                JsonNode usage = node.path("usage");
                if (!usage.isMissingNode()) {
                    input = Math.max(input, usage.path("input_tokens").asLong(0));
                    output = Math.max(output, usage.path("output_tokens").asLong(0));
                    cached = Math.max(cached, usage.path("cached_input_tokens").asLong(usage.path("cached_tokens").asLong(0)));
                }
            } catch (Exception ignored) {}
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (threadId != null) result.put("codexThreadId", threadId);
        result.put("inputTokens", input);
        result.put("outputTokens", output);
        result.put("cachedTokens", cached);
        result.put("observedCommandEvents", commands);
        result.put("billingMode", "CHATGPT_SUBSCRIPTION");
        return result;
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...[truncated]";
    }

    private record WorkspaceRoots(Path primary, List<Path> additional) {}
    private record ProcessResult(int exitCode, String output) {}
    public record Status(boolean installed, boolean enabled, String version, boolean loggedIn,
                         String loginStatus, List<AgentType> delegatedRoles) {}
}
