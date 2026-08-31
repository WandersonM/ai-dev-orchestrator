package com.ordevia.aidev.workspace.application;

import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;

@Component
public class GitWorktreeManager {
    private final LocalCommandExecutor executor;
    private final Path workspaceRoot;

    public GitWorktreeManager(LocalCommandExecutor executor, @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.executor = executor;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    public Worktree create(Path repository, String externalId) {
        String safeId = externalId.replaceAll("[^A-Za-z0-9._-]", "-");
        String branch = "ai/" + safeId;
        Path worktrees = workspaceRoot.resolve("worktrees");
        Path target = worktrees.resolve(safeId).normalize();
        if (!target.startsWith(workspaceRoot)) throw new SecurityException("Invalid worktree path");
        try { Files.createDirectories(worktrees); } catch (Exception e) { throw new IllegalStateException(e); }
        if (!Files.exists(target)) {
            var result = executor.execute(workspaceRoot, repository, List.of("git", "worktree", "add", target.toString(), "-b", branch), Duration.ofMinutes(2));
            if (result.exitCode() != 0) throw new IllegalStateException("Unable to create worktree: " + result.output());
        }
        return new Worktree(target, branch);
    }

    public String diff(Path worktree) {
        var result = executor.execute(workspaceRoot, worktree, List.of("git", "diff", "--", "."), Duration.ofMinutes(1));
        if (result.exitCode() != 0) throw new IllegalStateException("Unable to read git diff: " + result.output());
        return result.output();
    }

    public record Worktree(Path path, String branch) {}
}
