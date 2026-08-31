package com.ordevia.aidev.agent.tool;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Component
public class SearchCodeTool implements AgentTool {
    private static final Set<String> SKIPPED = Set.of(".git", "target", "build", "node_modules", ".idea");

    @Override public String name() { return "search_code"; }
    @Override public String description() { return "Search text recursively in source files. Argument: query"; }

    @Override
    public ToolResult execute(Path workspace, Map<String, Object> arguments) {
        String query = String.valueOf(arguments.getOrDefault("query", "")).trim();
        if (query.isEmpty()) return ToolResult.fail("query is required");
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(workspace)) {
            Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext() && matches.size() < 100) {
                Path file = iterator.next();
                if (!Files.isRegularFile(file) || skipped(workspace, file)) continue;
                try {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size() && matches.size() < 100; i++) {
                        if (lines.get(i).contains(query)) {
                            matches.add(workspace.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
                        }
                    }
                } catch (Exception ignored) { }
            }
            return ToolResult.ok(matches.isEmpty() ? "No matches" : String.join("\n", matches));
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage());
        }
    }

    private boolean skipped(Path workspace, Path file) {
        Path relative = workspace.relativize(file);
        for (Path part : relative) if (SKIPPED.contains(part.toString())) return true;
        return false;
    }
}
