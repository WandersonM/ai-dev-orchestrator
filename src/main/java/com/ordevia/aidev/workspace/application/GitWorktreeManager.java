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
    private final String defaultBaseBranch;

    public GitWorktreeManager(LocalCommandExecutor executor,
                              @Value("${aidev.workspace-root}") String workspaceRoot,
                              @Value("${aidev.github.base-branch:main}") String baseBranch) {
        this.executor = executor;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.defaultBaseBranch = baseBranch;
    }

    public Worktree create(Path repository, String externalId) {
        return create(repository, externalId, "default", defaultBaseBranch, "ai/");
    }

    public Worktree create(Path repository, String externalId, String repositoryAlias, String baseBranch, String branchPrefix) {
        String branch = branchName(externalId, branchPrefix);
        Path target = worktreePath(externalId, repositoryAlias);
        try {
            Files.createDirectories(target.getParent());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (!Files.exists(target)) {
            String baseRef = resolveBaseRef(repository, normalizedBaseBranch(baseBranch));
            var result = executor.execute(
                    workspaceRoot,
                    repository,
                    List.of("git", "worktree", "add", target.toString(), "-b", branch, baseRef),
                    Duration.ofMinutes(2));
            if (result.exitCode() != 0) {
                throw new IllegalStateException("Unable to create worktree for " + repositoryAlias + ": " + result.output());
            }
        }
        return new Worktree(target, branch, repositoryAlias, normalizedBaseBranch(baseBranch));
    }

    public Worktree existing(String externalId) {
        Path target = worktreePath(externalId, "default");
        if (!Files.isDirectory(target)) throw new IllegalStateException("Worktree does not exist: " + target);
        return new Worktree(target, branchName(externalId, "ai/"), "default", defaultBaseBranch);
    }

    public String diff(Path worktree) {
        var result = executor.execute(workspaceRoot, worktree, List.of("git", "diff", "--", "."), Duration.ofMinutes(1));
        if (result.exitCode() != 0) throw new IllegalStateException("Unable to read git diff: " + result.output());
        return result.output();
    }

    public String diffAll(List<Worktree> worktrees) {
        StringBuilder out = new StringBuilder();
        for (Worktree worktree : worktrees) {
            out.append("\n===== REPOSITORY: ").append(worktree.repositoryAlias()).append(" =====\n");
            out.append(diff(worktree.path())).append('\n');
        }
        return out.toString();
    }

    public Path taskRoot(String externalId) {
        Path target = workspaceRoot.resolve("worktrees").resolve(safeId(externalId)).normalize();
        if (!target.startsWith(workspaceRoot)) throw new SecurityException("Invalid task workspace path");
        return target;
    }

    private String resolveBaseRef(Path repository, String baseBranch) {
        var remote = executor.execute(workspaceRoot, repository, List.of("git", "remote", "get-url", "origin"), Duration.ofSeconds(30));
        if (remote.exitCode() != 0) return baseBranch.equals(defaultBaseBranch) ? "HEAD" : baseBranch;

        var fetch = executor.execute(
                workspaceRoot,
                repository,
                List.of("git", "fetch", "origin", baseBranch),
                Duration.ofMinutes(2));
        if (fetch.exitCode() != 0) {
            throw new IllegalStateException("Unable to refresh origin/" + baseBranch + ": " + fetch.output());
        }
        return "origin/" + baseBranch;
    }

    private Path worktreePath(String externalId, String repositoryAlias) {
        Path target = taskRoot(externalId).resolve(safeId(repositoryAlias)).normalize();
        if (!target.startsWith(taskRoot(externalId))) throw new SecurityException("Invalid worktree path");
        return target;
    }

    private String branchName(String externalId, String branchPrefix) {
        String prefix = branchPrefix == null || branchPrefix.isBlank() ? "ai/" : branchPrefix;
        return prefix + safeId(externalId);
    }

    private String normalizedBaseBranch(String baseBranch) {
        return baseBranch == null || baseBranch.isBlank() ? defaultBaseBranch : baseBranch;
    }

    private String safeId(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "-"); }

    public record Worktree(Path path, String branch, String repositoryAlias, String baseBranch) {}
}
