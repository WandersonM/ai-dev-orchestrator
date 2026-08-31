package com.ordevia.aidev.workspace.application;

import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.infrastructure.ProjectRepositoryJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemRepositoryBinding;
import com.ordevia.aidev.workitem.infrastructure.WorkItemRepositoryBindingJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class MultiRepositoryWorkspaceManager {
    private final WorkItemRepositoryBindingJpaRepository bindings;
    private final ProjectRepositoryJpaRepository repositories;
    private final GitWorktreeManager worktrees;
    private final Path workspaceRoot;

    public MultiRepositoryWorkspaceManager(WorkItemRepositoryBindingJpaRepository bindings,
                                           ProjectRepositoryJpaRepository repositories,
                                           GitWorktreeManager worktrees,
                                           @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.bindings = bindings;
        this.repositories = repositories;
        this.worktrees = worktrees;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    public TaskWorkspace prepare(WorkItem item) {
        List<WorkItemRepositoryBinding> itemBindings = bindings.findByWorkItemIdOrderByCreatedAtAsc(item.getId());
        if (itemBindings.isEmpty()) {
            Path source = sourcePath(item.getRepositoryPath());
            GitWorktreeManager.Worktree worktree = worktrees.create(source, item.getExternalId());
            return new TaskWorkspace(worktree.path(), List.of(worktree), manifest(List.of(worktree), List.of()));
        }

        List<GitWorktreeManager.Worktree> prepared = new ArrayList<>();
        List<ProjectRepository> profiles = new ArrayList<>();
        for (WorkItemRepositoryBinding binding : itemBindings) {
            ProjectRepository profile = repositories.findById(binding.getProjectRepositoryId())
                    .orElseThrow(() -> new IllegalStateException("Project repository binding points to missing profile"));
            if (!profile.isEnabled()) throw new IllegalStateException("Repository profile is disabled: " + profile.getAlias());
            String baseBranch = binding.getBaseBranchOverride() == null || binding.getBaseBranchOverride().isBlank()
                    ? profile.getBaseBranch() : binding.getBaseBranchOverride();
            GitWorktreeManager.Worktree worktree = worktrees.create(
                    sourcePath(profile.getRepositoryPath()), item.getExternalId(), profile.getAlias(), baseBranch, profile.getBranchPrefix());
            prepared.add(worktree);
            profiles.add(profile);
        }
        return new TaskWorkspace(worktrees.taskRoot(item.getExternalId()), List.copyOf(prepared), manifest(prepared, profiles));
    }

    private Path sourcePath(String repositoryPath) {
        Path path = workspaceRoot.resolve(repositoryPath).normalize();
        if (!path.startsWith(workspaceRoot)) throw new SecurityException("Repository outside workspace root");
        return path;
    }

    private String manifest(List<GitWorktreeManager.Worktree> worktrees, List<ProjectRepository> profiles) {
        StringBuilder out = new StringBuilder();
        out.append("MULTI-ROOT WORKSPACE\n");
        for (int i = 0; i < worktrees.size(); i++) {
            GitWorktreeManager.Worktree wt = worktrees.get(i);
            ProjectRepository profile = profiles.isEmpty() ? null : profiles.get(i);
            out.append("- root: ").append(wt.repositoryAlias()).append("/")
                    .append(" | branch: ").append(wt.branch())
                    .append(" | base: ").append(wt.baseBranch());
            if (profile != null) {
                out.append(" | kind: ").append(profile.getKind());
                if (profile.getJavaVersion() != null) out.append(" | java: ").append(profile.getJavaVersion());
                if (profile.getNodeVersion() != null) out.append(" | node: ").append(profile.getNodeVersion());
                if (profile.getBuildCommand() != null) out.append(" | build: ").append(profile.getBuildCommand());
                if (profile.getTestCommand() != null) out.append(" | test: ").append(profile.getTestCommand());
                if (profile.getInstructionsPath() != null) out.append(" | instructions: ").append(profile.getInstructionsPath());
            }
            out.append('\n');
        }
        out.append("All file paths passed to tools are relative to this task workspace root. For example: backend/src/... or legacy/pom.xml.\n");
        return out.toString();
    }

    public record TaskWorkspace(Path root, List<GitWorktreeManager.Worktree> worktrees, String manifest) {}
}
