package com.ordevia.aidev.agent.tool;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

@Component
public class ReadFileTool implements AgentTool {
    @Override public String name() { return "read_file"; }
    @Override public String description() { return "Read a UTF-8 text file inside the current workspace. Argument: path"; }

    @Override
    public ToolResult execute(Path workspace, Map<String, Object> arguments) {
        try {
            Path file = safeResolve(workspace, String.valueOf(arguments.get("path")));
            if (!Files.isRegularFile(file)) return ToolResult.fail("File not found: " + file);
            return ToolResult.ok(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    private Path safeResolve(Path workspace, String value) {
        Path root = workspace.toAbsolutePath().normalize();
        Path target = root.resolve(value).normalize();
        if (!target.startsWith(root)) throw new SecurityException("Path outside workspace");
        return target;
    }
}
