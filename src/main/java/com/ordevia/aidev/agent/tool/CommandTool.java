package com.ordevia.aidev.agent.tool;

import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Component
public class CommandTool implements AgentTool {
    private final LocalCommandExecutor executor;
    private final Path workspaceRoot;

    public CommandTool(LocalCommandExecutor executor, @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.executor = executor;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override public String name() { return "run_command"; }
    @Override public String description() { return "Run an allow-listed command in the workspace. Argument: command as array of strings"; }

    @Override
    public ToolResult execute(Path workspace, Map<String, Object> arguments) {
        try {
            Object raw = arguments.get("command");
            if (!(raw instanceof List<?> values)) return ToolResult.fail("command must be an array");
            List<String> command = values.stream().map(String::valueOf).toList();
            var result = executor.execute(workspaceRoot, workspace, command, Duration.ofMinutes(5));
            return result.exitCode() == 0 ? ToolResult.ok(result.output()) : ToolResult.fail(result.output());
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }
}
