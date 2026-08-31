package com.ordevia.aidev.agent.tool;

import com.ordevia.aidev.agent.domain.AgentContext;
import com.ordevia.aidev.execution.application.ExecutionRouter;
import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class CommandTool implements AgentTool {
    private final LocalCommandExecutor localExecutor;
    private final ExecutionRouter executionRouter;
    private final Path workspaceRoot;

    public CommandTool(LocalCommandExecutor localExecutor,
                       ExecutionRouter executionRouter,
                       @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.localExecutor = localExecutor;
        this.executionRouter = executionRouter;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override public String name() { return "run_command"; }
    @Override public String description() { return "Run an allow-listed command using the repository execution profile. Arguments: command array and optional cwd relative to the task workspace root."; }

    @Override
    public ToolResult execute(Path workspace, Map<String, Object> arguments) {
        try {
            List<String> command = command(arguments);
            var result = localExecutor.execute(workspaceRoot, workspace, command, Duration.ofMinutes(5));
            return result.exitCode() == 0 ? ToolResult.ok(result.output()) : ToolResult.fail(result.output());
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    @Override
    public ToolResult execute(AgentContext context, Map<String, Object> arguments) {
        try {
            List<String> command = command(arguments);
            Path taskRoot = context.repository().toAbsolutePath().normalize();
            String cwdArg = arguments.get("cwd") == null ? "" : String.valueOf(arguments.get("cwd")).trim();
            Path cwd = cwdArg.isBlank() ? taskRoot : taskRoot.resolve(cwdArg).normalize();
            if (!cwd.startsWith(taskRoot)) throw new SecurityException("cwd outside task workspace");
            var result = executionRouter.execute(context.workItemId(), taskRoot, cwd, command);
            String decorated = "[backend=" + result.backend() + "]\n" + result.output();
            return result.success() ? ToolResult.ok(decorated) : ToolResult.fail(decorated);
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    private List<String> command(Map<String, Object> arguments) {
        Object raw = arguments.get("command");
        if (!(raw instanceof List<?> values)) throw new IllegalArgumentException("command must be an array");
        List<String> command = values.stream().map(String::valueOf).toList();
        if (command.isEmpty()) throw new IllegalArgumentException("command cannot be empty");
        return command;
    }
}
