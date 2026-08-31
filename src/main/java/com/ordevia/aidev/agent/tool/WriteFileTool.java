package com.ordevia.aidev.agent.tool;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

@Component
public class WriteFileTool implements AgentTool {
    @Override public String name() { return "write_file"; }
    @Override public String description() { return "Create or replace a UTF-8 text file inside the current workspace. Arguments: path, content"; }

    @Override
    public ToolResult execute(Path workspace, Map<String, Object> arguments) {
        try {
            Path root = workspace.toAbsolutePath().normalize();
            Path target = root.resolve(String.valueOf(arguments.get("path"))).normalize();
            if (!target.startsWith(root)) throw new SecurityException("Path outside workspace");
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.writeString(target, String.valueOf(arguments.getOrDefault("content", "")), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return ToolResult.ok("Wrote " + root.relativize(target));
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }
}
