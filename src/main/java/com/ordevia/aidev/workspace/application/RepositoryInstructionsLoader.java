package com.ordevia.aidev.workspace.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class RepositoryInstructionsLoader {
    private static final int MAX_CHARS_PER_FILE = 20_000;
    private static final int MAX_TOTAL_CHARS = 50_000;

    public String load(Path repositoryRoot, String configuredInstructionsPath) {
        Set<String> candidates = new LinkedHashSet<>();
        if (configuredInstructionsPath != null && !configuredInstructionsPath.isBlank()) candidates.add(configuredInstructionsPath);
        candidates.add("AGENTS.md");
        candidates.add(".ai/AGENTS.md");
        candidates.add(".ai/ARCHITECTURE.md");
        candidates.add(".ai/DOMAIN.md");
        candidates.add(".ai/CONVENTIONS.md");
        candidates.add(".ai/TESTING.md");
        candidates.add(".ai/SECURITY.md");

        StringBuilder out = new StringBuilder();
        for (String candidate : candidates) {
            if (out.length() >= MAX_TOTAL_CHARS) break;
            Path file = repositoryRoot.resolve(candidate).normalize();
            if (!file.startsWith(repositoryRoot.normalize()) || !Files.isRegularFile(file)) continue;
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.length() > MAX_CHARS_PER_FILE) content = content.substring(0, MAX_CHARS_PER_FILE) + "\n...[truncated]";
                int remaining = MAX_TOTAL_CHARS - out.length();
                if (content.length() > remaining) content = content.substring(0, Math.max(0, remaining));
                out.append("\n### ").append(candidate).append("\n").append(content).append('\n');
            } catch (Exception e) {
                out.append("\n### ").append(candidate).append("\n<unable to read: ").append(e.getMessage()).append(">\n");
            }
        }
        return out.toString();
    }
}
