package com.ordevia.aidev.workspace.application;

import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
public class RepositoryCodeMapService {
    private static final int MAX_LISTED_FILES = 450;
    private final LocalCommandExecutor commands;
    private final Path workspaceRoot;
    private final Path cacheRoot;

    public RepositoryCodeMapService(LocalCommandExecutor commands,
                                    @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.commands = commands;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.cacheRoot = this.workspaceRoot.resolve(".aidev-cache/code-map");
    }

    public String map(Path repositoryRoot) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        if (!root.startsWith(workspaceRoot)) return "Code map unavailable: repository is outside configured workspace root.";
        try {
            String revision = revision(root);
            String key = Integer.toHexString(root.toString().hashCode()) + "-" + revision.replaceAll("[^A-Za-z0-9._-]", "_");
            Path cached = cacheRoot.resolve(key + ".txt").normalize();
            if (cached.startsWith(cacheRoot) && Files.isRegularFile(cached)) return Files.readString(cached, StandardCharsets.UTF_8);
            String generated = generate(root, revision);
            Files.createDirectories(cacheRoot);
            Files.writeString(cached, generated, StandardCharsets.UTF_8);
            return generated;
        } catch (Exception e) {
            return "Code map unavailable: " + e.getMessage();
        }
    }

    private String revision(Path root) {
        var result = commands.execute(workspaceRoot, root, List.of("git", "rev-parse", "HEAD"), Duration.ofSeconds(20));
        return result.exitCode() == 0 ? result.output().trim() : "working-tree";
    }

    private String generate(Path root, String revision) {
        var result = commands.execute(workspaceRoot, root, List.of("git", "ls-files"), Duration.ofSeconds(30));
        if (result.exitCode() != 0) return "Code map unavailable: " + result.output();
        List<String> files = result.output().lines().filter(s -> !s.isBlank()).toList();
        Map<String,Integer> top = new TreeMap<>();
        Map<String,Integer> extensions = new TreeMap<>();
        List<String> important = new ArrayList<>();
        List<String> representative = new ArrayList<>();
        for (String file : files) {
            String first = file.contains("/") ? file.substring(0,file.indexOf('/')) : "<root>";
            top.merge(first,1,Integer::sum);
            String name = file.substring(file.lastIndexOf('/')+1);
            int dot = name.lastIndexOf('.');
            if (dot > 0) extensions.merge(name.substring(dot+1).toLowerCase(Locale.ROOT),1,Integer::sum);
            if (isImportant(name,file)) important.add(file);
            if (representative.size() < MAX_LISTED_FILES && representativePath(file)) representative.add(file);
        }
        StringBuilder out = new StringBuilder("CODEBASE MAP\nrevision: ").append(revision).append("\ntrackedFiles: ").append(files.size()).append("\n\nTop-level areas:\n");
        top.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(40)
                .forEach(e -> out.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append(" files\n"));
        out.append("\nDominant extensions:\n");
        extensions.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(20)
                .forEach(e -> out.append("- .").append(e.getKey()).append(": ").append(e.getValue()).append('\n'));
        if (!important.isEmpty()) {
            out.append("\nBuild/config/architecture markers:\n");
            important.stream().limit(120).forEach(f -> out.append("- ").append(f).append('\n'));
        }
        if (!representative.isEmpty()) {
            out.append("\nRepresentative source/test paths (search for details instead of assuming):\n");
            representative.forEach(f -> out.append("- ").append(f).append('\n'));
        }
        return out.toString();
    }

    private boolean isImportant(String name, String file) {
        return Set.of("pom.xml","build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts","package.json","pnpm-workspace.yaml",
                "composer.json","Dockerfile","docker-compose.yml","docker-compose.yaml","AGENTS.md","README.md","application.yml","application.yaml")
                .contains(name) || file.startsWith(".ai/") || file.startsWith(".github/workflows/") || file.contains("/db/migration/");
    }

    private boolean representativePath(String file) {
        return file.contains("/src/main/") || file.contains("/src/test/") || file.startsWith("src/") || file.contains("/app/") || file.contains("/packages/");
    }
}
