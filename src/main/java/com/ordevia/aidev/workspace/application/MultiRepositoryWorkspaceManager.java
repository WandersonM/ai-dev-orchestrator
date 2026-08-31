package com.ordevia.aidev.workspace.application;

import com.ordevia.aidev.execution.application.EnvironmentPreparationService;
import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.infrastructure.ProjectRepositoryJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemRepositoryBinding;
import com.ordevia.aidev.workitem.infrastructure.WorkItemRepositoryBindingJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Component
public class MultiRepositoryWorkspaceManager {
    private final WorkItemRepositoryBindingJpaRepository bindings;
    private final ProjectRepositoryJpaRepository repositories;
    private final GitWorktreeManager worktrees;
    private final RepositoryInstructionsLoader instructionsLoader;
    private final EnvironmentPreparationService environmentPreparation;
    private final Path workspaceRoot;

    public MultiRepositoryWorkspaceManager(WorkItemRepositoryBindingJpaRepository bindings,
                                           ProjectRepositoryJpaRepository repositories,
                                           GitWorktreeManager worktrees,
                                           RepositoryInstructionsLoader instructionsLoader,
                                           EnvironmentPreparationService environmentPreparation,
                                           @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.bindings=bindings; this.repositories=repositories; this.worktrees=worktrees; this.instructionsLoader=instructionsLoader;
        this.environmentPreparation=environmentPreparation; this.workspaceRoot=Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    public TaskWorkspace prepare(WorkItem item) {
        if (item.getActiveWorkspacePath()!=null && !item.getActiveWorkspacePath().isBlank()) {
            return existingWorkspace(item, Path.of(item.getActiveWorkspacePath()));
        }
        List<WorkItemRepositoryBinding> itemBindings=bindings.findByWorkItemIdOrderByCreatedAtAsc(item.getId());
        if(itemBindings.isEmpty()){
            Path source=sourcePath(item.getRepositoryPath());
            GitWorktreeManager.Worktree worktree=worktrees.create(source,item.getExternalId());
            String instructions=instructionsLoader.load(worktree.path(),null);
            return new TaskWorkspace(worktree.path(),List.of(worktree),manifest(List.of(worktree),List.of(),List.of(instructions)));
        }
        List<GitWorktreeManager.Worktree> prepared=new ArrayList<>(); List<ProjectRepository> profiles=new ArrayList<>(); List<String> instructions=new ArrayList<>();
        for(WorkItemRepositoryBinding binding:itemBindings){
            ProjectRepository profile=repositories.findById(binding.getProjectRepositoryId()).orElseThrow(()->new IllegalStateException("Project repository binding points to missing profile"));
            if(!profile.isEnabled())throw new IllegalStateException("Repository profile is disabled: "+profile.getAlias());
            String baseBranch=binding.getBaseBranchOverride()==null||binding.getBaseBranchOverride().isBlank()?profile.getBaseBranch():binding.getBaseBranchOverride();
            GitWorktreeManager.Worktree worktree=worktrees.create(sourcePath(profile.getRepositoryPath()),item.getExternalId(),profile.getAlias(),baseBranch,profile.getBranchPrefix());
            prepared.add(worktree); profiles.add(profile); instructions.add(instructionsLoader.load(worktree.path(),profile.getInstructionsPath()));
        }
        Path taskRoot=worktrees.taskRoot(item.getExternalId()); environmentPreparation.prepare(item,taskRoot);
        return new TaskWorkspace(taskRoot,List.copyOf(prepared),manifest(prepared,profiles,instructions));
    }

    private TaskWorkspace existingWorkspace(WorkItem item, Path configuredRoot) {
        Path root=configuredRoot.toAbsolutePath().normalize();
        if(!root.startsWith(workspaceRoot))throw new SecurityException("Active workspace outside configured root");
        List<WorkItemRepositoryBinding> itemBindings=bindings.findByWorkItemIdOrderByCreatedAtAsc(item.getId());
        if(itemBindings.isEmpty()){
            if(!Files.exists(root.resolve(".git")))throw new IllegalStateException("Active workspace is not a git worktree: "+root);
            var wt=worktrees.describeExisting(root,"default",null);
            return new TaskWorkspace(root,List.of(wt),manifest(List.of(wt),List.of(),List.of(instructionsLoader.load(root,null))));
        }
        List<GitWorktreeManager.Worktree> prepared=new ArrayList<>(); List<ProjectRepository> profiles=new ArrayList<>(); List<String> instructions=new ArrayList<>();
        for(WorkItemRepositoryBinding binding:itemBindings){
            ProjectRepository profile=repositories.findById(binding.getProjectRepositoryId()).orElseThrow(()->new IllegalStateException("Project repository binding points to missing profile"));
            String base=binding.getBaseBranchOverride()==null||binding.getBaseBranchOverride().isBlank()?profile.getBaseBranch():binding.getBaseBranchOverride();
            Path repoRoot=root.resolve(profile.getAlias()).normalize();
            var wt=worktrees.describeExisting(repoRoot,profile.getAlias(),base);
            prepared.add(wt); profiles.add(profile); instructions.add(instructionsLoader.load(repoRoot,profile.getInstructionsPath()));
        }
        environmentPreparation.prepare(item,root);
        return new TaskWorkspace(root,List.copyOf(prepared),manifest(prepared,profiles,instructions));
    }

    private Path sourcePath(String repositoryPath){Path path=workspaceRoot.resolve(repositoryPath).normalize();if(!path.startsWith(workspaceRoot))throw new SecurityException("Repository outside workspace root");return path;}

    private String manifest(List<GitWorktreeManager.Worktree> worktrees,List<ProjectRepository> profiles,List<String> instructions){
        StringBuilder out=new StringBuilder();out.append("MULTI-ROOT WORKSPACE\n");
        for(int i=0;i<worktrees.size();i++){
            var wt=worktrees.get(i);ProjectRepository profile=profiles.isEmpty()?null:profiles.get(i);
            out.append("\n## Repository root: ").append(wt.repositoryAlias()).append("/\n").append("branch: ").append(wt.branch()).append("\n").append("base: ").append(wt.baseBranch()).append("\n");
            if(profile!=null){out.append("kind: ").append(profile.getKind()).append('\n');if(profile.getJavaVersion()!=null)out.append("java: ").append(profile.getJavaVersion()).append('\n');if(profile.getNodeVersion()!=null)out.append("node: ").append(profile.getNodeVersion()).append('\n');if(profile.getBuildCommand()!=null)out.append("build: ").append(profile.getBuildCommand()).append('\n');if(profile.getTestCommand()!=null)out.append("test: ").append(profile.getTestCommand()).append('\n');if(profile.getInstructionsPath()!=null)out.append("instructionsPath: ").append(profile.getInstructionsPath()).append('\n');}
            String repositoryInstructions=instructions.size()>i?instructions.get(i):"";if(repositoryInstructions!=null&&!repositoryInstructions.isBlank())out.append("\n### Versioned repository instructions\n").append(repositoryInstructions).append('\n');
        }
        out.append("\nAll file paths passed to tools are relative to this task workspace root. For run_command use cwd with the repository alias in multi-root tasks.\n");return out.toString();
    }

    public record TaskWorkspace(Path root,List<GitWorktreeManager.Worktree> worktrees,String manifest){}
}
