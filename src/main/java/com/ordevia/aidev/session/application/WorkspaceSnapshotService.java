package com.ordevia.aidev.session.application;

import com.ordevia.aidev.session.domain.AgentWorkspaceSnapshot;
import com.ordevia.aidev.session.infrastructure.AgentWorkspaceSnapshotJpaRepository;
import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

@Service
public class WorkspaceSnapshotService {
    private final AgentWorkspaceSnapshotJpaRepository snapshots;
    private final LocalCommandExecutor commands;
    private final Path workspaceRoot;

    public WorkspaceSnapshotService(AgentWorkspaceSnapshotJpaRepository snapshots,
                                    LocalCommandExecutor commands,
                                    @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.snapshots = snapshots;
        this.commands = commands;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public List<AgentWorkspaceSnapshot> capture(UUID sessionId, UUID checkpointId, Path taskRoot) {
        if (snapshots.existsByCheckpointId(checkpointId)) return snapshots.findByCheckpointIdOrderByRepositoryAliasAsc(checkpointId);
        List<RepoRoot> roots = repositoryRoots(taskRoot);
        if (roots.isEmpty()) throw new IllegalStateException("No git worktree found under task workspace: " + taskRoot);
        List<AgentWorkspaceSnapshot> captured = new ArrayList<>();
        for (RepoRoot repo : roots) captured.add(captureRepo(sessionId, checkpointId, repo));
        return snapshots.saveAll(captured);
    }

    @Transactional(readOnly = true)
    public List<AgentWorkspaceSnapshot> listByCheckpoint(UUID checkpointId) {
        return snapshots.findByCheckpointIdOrderByRepositoryAliasAsc(checkpointId);
    }

    @Transactional(readOnly = true)
    public List<AgentWorkspaceSnapshot> listBySession(UUID sessionId) {
        return snapshots.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public Path createForkWorkspace(UUID checkpointId, UUID newSessionId, String externalId) {
        List<AgentWorkspaceSnapshot> state = snapshots.findByCheckpointIdOrderByRepositoryAliasAsc(checkpointId);
        if (state.isEmpty()) throw new IllegalStateException("Checkpoint is not restorable: no workspace snapshot exists");
        Path base = workspaceRoot.resolve("worktrees").resolve("forks").resolve(safe(externalId) + "-" + newSessionId.toString().substring(0, 8)).normalize();
        if (!base.startsWith(workspaceRoot)) throw new SecurityException("Invalid fork workspace path");
        try { Files.createDirectories(base.getParent()); } catch (Exception e) { throw new IllegalStateException("Unable to create fork workspace", e); }

        boolean singleDefault = state.size() == 1 && "default".equals(state.getFirst().getRepositoryAlias());
        for (AgentWorkspaceSnapshot snapshot : state) {
            Path source = Path.of(snapshot.getWorktreePath()).toAbsolutePath().normalize();
            Path target = singleDefault ? base : base.resolve(safe(snapshot.getRepositoryAlias())).normalize();
            String branch = "aidev/fork/" + safe(externalId) + "-" + newSessionId.toString().substring(0, 8) + "-" + safe(snapshot.getRepositoryAlias());
            var result = commands.execute(workspaceRoot, source,
                    List.of("git", "worktree", "add", target.toString(), "-b", branch, snapshot.getSnapshotCommitSha()), Duration.ofMinutes(2));
            if (result.exitCode() != 0) throw new IllegalStateException("Unable to create fork worktree for " + snapshot.getRepositoryAlias() + ": " + result.output());
        }
        return base;
    }

    private AgentWorkspaceSnapshot captureRepo(UUID sessionId, UUID checkpointId, RepoRoot repo) {
        String head = git(repo.path(), List.of("git", "rev-parse", "HEAD"), Map.of()).trim();
        String branch = git(repo.path(), List.of("git", "branch", "--show-current"), Map.of()).trim();
        Path temp = workspaceRoot.resolve(".aidev").resolve("indexes").resolve(UUID.randomUUID() + ".index").normalize();
        try { Files.createDirectories(temp.getParent()); Files.deleteIfExists(temp); }
        catch (Exception e) { throw new IllegalStateException("Unable to prepare snapshot index", e); }
        Map<String,String> env = new LinkedHashMap<>();
        env.put("GIT_INDEX_FILE", temp.toString());
        env.put("GIT_AUTHOR_NAME", "AI Dev Orchestrator");
        env.put("GIT_AUTHOR_EMAIL", "aidev@localhost");
        env.put("GIT_COMMITTER_NAME", "AI Dev Orchestrator");
        env.put("GIT_COMMITTER_EMAIL", "aidev@localhost");
        try {
            git(repo.path(), List.of("git", "read-tree", "HEAD"), env);
            git(repo.path(), List.of("git", "add", "-A"), env);
            String tree = git(repo.path(), List.of("git", "write-tree"), env).trim();
            String commit = git(repo.path(), List.of("git", "commit-tree", tree, "-p", head, "-m", "aidev checkpoint " + checkpointId), env).trim();
            String ref = "refs/aidev/snapshots/" + sessionId + "/" + checkpointId + "/" + safe(repo.alias());
            git(repo.path(), List.of("git", "update-ref", ref, commit), Map.of());
            return new AgentWorkspaceSnapshot(UUID.randomUUID(), checkpointId, sessionId, repo.alias(), repo.path().toString(), head, commit, branch);
        } finally {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }

    private String git(Path cwd, List<String> command, Map<String,String> env) {
        var result = env.isEmpty()
                ? commands.execute(workspaceRoot, cwd, command, Duration.ofMinutes(2))
                : commands.executeIsolated(workspaceRoot, cwd, command, Duration.ofMinutes(2), env);
        if (result.exitCode() != 0) throw new IllegalStateException("Git snapshot command failed: " + result.output());
        return result.output();
    }

    private List<RepoRoot> repositoryRoots(Path taskRoot) {
        Path root = taskRoot.toAbsolutePath().normalize();
        if (!root.startsWith(workspaceRoot)) throw new SecurityException("Task workspace outside configured root");
        if (isGitWorktree(root)) return List.of(new RepoRoot("default", root));
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory).filter(this::isGitWorktree)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> new RepoRoot(p.getFileName().toString(), p)).toList();
        } catch (Exception e) { throw new IllegalStateException("Unable to inspect task workspace", e); }
    }

    private boolean isGitWorktree(Path path) { return Files.exists(path.resolve(".git")); }
    private String safe(String value) { return Objects.toString(value, "default").replaceAll("[^A-Za-z0-9._-]", "-"); }
    private record RepoRoot(String alias, Path path) {}
}
