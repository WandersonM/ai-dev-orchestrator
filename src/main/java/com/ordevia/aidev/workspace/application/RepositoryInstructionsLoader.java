package com.ordevia.aidev.workspace.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Component
public class RepositoryInstructionsLoader {
    private static final int MAX_CHARS_PER_FILE = 20_000;
    private static final int MAX_TOTAL_CHARS = 60_000;
    private static final int MAX_SCOPED_FILES = 20;
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", "target", "build", "dist", "node_modules", "vendor", ".gradle", ".next", "coverage");

    public String load(Path repositoryRoot, String configuredInstructionsPath) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        LinkedHashMap<Path, String> candidates = new LinkedHashMap<>();
        if (configuredInstructionsPath != null && !configuredInstructionsPath.isBlank()) {
            addCandidate(candidates, root, configuredInstructionsPath, "repository configured instructions");
        }
        addCandidate(candidates, root, "AGENTS.md", "repository root");
        addCandidate(candidates, root, ".ai/AGENTS.md", "repository root");
        addCandidate(candidates, root, ".ai/ARCHITECTURE.md", "repository architecture");
        addCandidate(candidates, root, ".ai/DOMAIN.md", "repository domain");
        addCandidate(candidates, root, ".ai/CONVENTIONS.md", "repository conventions");
        addCandidate(candidates, root, ".ai/TESTING.md", "repository testing");
        addCandidate(candidates, root, ".ai/SECURITY.md", "repository security");
        discoverScopedAgents(root, candidates);

        StringBuilder out = new StringBuilder();
        for (Map.Entry<Path, String> entry : candidates.entrySet()) {
            if (out.length() >= MAX_TOTAL_CHARS) break;
            Path file = entry.getKey();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) continue;
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.length() > MAX_CHARS_PER_FILE) content = content.substring(0, MAX_CHARS_PER_FILE) + "\n...[truncated]";
                int remaining = MAX_TOTAL_CHARS - out.length();
                if (content.length() > remaining) content = content.substring(0, Math.max(0, remaining));
                String relative = root.relativize(file).toString().replace('\\','/');
                out.append("\n### Instructions: ").append(relative).append("\n")
                        .append("Scope: ").append(entry.getValue()).append("\n")
                        .append("More specific nested AGENTS.md instructions override broader instructions for files inside their scope.\n")
                        .append(content).append('\n');
            } catch (Exception e) {
                String relative = root.relativize(file).toString().replace('\\','/');
                out.append("\n### Instructions: ").append(relative).append("\n<unable to read: ").append(e.getMessage()).append(">\n");
            }
        }
        return out.toString();
    }

    private void discoverScopedAgents(Path root, LinkedHashMap<Path, String> candidates) {
        try (Stream<Path> stream = Files.walk(root, 7)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> "AGENTS.md".equals(path.getFileName().toString()))
                    .filter(path -> !path.getParent().equals(root))
                    .filter(path -> !ignored(root, path))
                    .sorted(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString))
                    .limit(MAX_SCOPED_FILES)
                    .forEach(path -> {
                        Path scope = root.relativize(path.getParent());
                        candidates.putIfAbsent(path.toAbsolutePath().normalize(), portable(scope) + "/**");
                    });
        } catch (Exception ignored) {
            // Root-level instructions still work when recursive discovery is unavailable.
        }
    }

    private boolean ignored(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path part : relative) if (IGNORED_DIRECTORIES.contains(part.toString())) return true;
        return false;
    }

    private void addCandidate(Map<Path, String> candidates, Path root, String relative, String scope) {
        Path file = root.resolve(relative).normalize();
        if (file.startsWith(root)) candidates.putIfAbsent(file, scope);
    }

    private String portable(Path path) {
        String value = path.toString().replace('\\','/');
        return value.isBlank() ? "." : value;
    }
}
