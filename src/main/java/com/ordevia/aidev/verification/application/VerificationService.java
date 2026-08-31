package com.ordevia.aidev.verification.application;

import com.ordevia.aidev.execution.application.ExecutionRouter;
import com.ordevia.aidev.execution.application.SafeCommandLineParser;
import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.infrastructure.ProjectRepositoryJpaRepository;
import com.ordevia.aidev.verification.domain.*;
import com.ordevia.aidev.verification.infrastructure.*;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import com.ordevia.aidev.workspace.application.MultiRepositoryWorkspaceManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class VerificationService {
    private final WorkItemJpaRepository workItems;
    private final ProjectRepositoryJpaRepository repositories;
    private final RepositoryVerificationProfileJpaRepository profiles;
    private final VerificationRunJpaRepository runs;
    private final VerificationRunItemJpaRepository items;
    private final MultiRepositoryWorkspaceManager workspaces;
    private final ExecutionRouter execution;
    private final SafeCommandLineParser parser;

    public VerificationService(WorkItemJpaRepository workItems,ProjectRepositoryJpaRepository repositories,
                               RepositoryVerificationProfileJpaRepository profiles,VerificationRunJpaRepository runs,
                               VerificationRunItemJpaRepository items,MultiRepositoryWorkspaceManager workspaces,
                               ExecutionRouter execution,SafeCommandLineParser parser){
        this.workItems=workItems;this.repositories=repositories;this.profiles=profiles;this.runs=runs;this.items=items;this.workspaces=workspaces;this.execution=execution;this.parser=parser;
    }

    @Transactional
    public RepositoryVerificationProfile configure(UUID projectId,UUID repositoryId,VerificationProfileCommand command){
        ProjectRepository repository=repositories.findById(repositoryId).orElseThrow(()->new NoSuchElementException("Project repository not found"));
        if(!repository.getProjectId().equals(projectId))throw new IllegalArgumentException("Repository does not belong to project");
        validateOptional(command.lintCommand());validateOptional(command.coverageCommand());validateOptional(command.contractCommand());validateOptional(command.migrationCommand());
        RepositoryVerificationProfile profile=profiles.findByProjectRepositoryId(repositoryId)
                .orElseGet(()->new RepositoryVerificationProfile(UUID.randomUUID(),repositoryId,command.lintCommand(),command.coverageCommand(),command.contractCommand(),command.migrationCommand()));
        profile.update(command.lintCommand(),command.coverageCommand(),command.contractCommand(),command.migrationCommand(),command.enabled());
        return profiles.save(profile);
    }

    @Transactional(readOnly=true)
    public Optional<RepositoryVerificationProfile> profile(UUID projectId,UUID repositoryId){
        ProjectRepository repository=repositories.findById(repositoryId).orElseThrow(()->new NoSuchElementException("Project repository not found"));
        if(!repository.getProjectId().equals(projectId))throw new IllegalArgumentException("Repository does not belong to project");
        return profiles.findByProjectRepositoryId(repositoryId);
    }

    public VerificationResult run(UUID workItemId){
        WorkItem item=workItems.findById(workItemId).orElseThrow(()->new NoSuchElementException("WorkItem not found"));
        var workspace=workspaces.prepare(item);
        VerificationRun run=runs.save(new VerificationRun(UUID.randomUUID(),workItemId));
        List<VerificationRunItem> evidence=new ArrayList<>(); boolean passed=true;
        for(var wt:workspace.worktrees()){
            ProjectRepository repo=item.getProjectId()==null?null:repositories.findByProjectIdAndAlias(item.getProjectId(),wt.repositoryAlias()).orElse(null);
            List<Check> checks=checks(repo);
            for(Check check:checks){
                VerificationRunItem e=new VerificationRunItem(UUID.randomUUID(),run.getId(),repo==null?null:repo.getId(),wt.repositoryAlias(),check.type(),check.command());
                items.save(e);
                try{
                    var result=execution.execute(workItemId,workspace.root(),wt.path(),parser.parse(check.command()));
                    e.finish(result.exitCode(),result.output());
                }catch(Exception ex){e.finish(1,ex.getMessage());}
                items.save(e);evidence.add(e);if(e.getStatus()!=VerificationStatus.PASSED)passed=false;
            }
        }
        run.finish(passed);runs.save(run);return new VerificationResult(run,List.copyOf(evidence));
    }

    @Transactional(readOnly=true) public List<VerificationRun> runs(UUID workItemId){return runs.findByWorkItemIdOrderByStartedAtDesc(workItemId);}
    @Transactional(readOnly=true) public List<VerificationRunItem> items(UUID runId){return items.findByVerificationRunIdOrderByStartedAtAsc(runId);}

    private List<Check> checks(ProjectRepository repo){
        if(repo==null)return List.of(); List<Check> checks=new ArrayList<>();
        add(checks,"BUILD",repo.getBuildCommand());add(checks,"TEST",repo.getTestCommand());
        profiles.findByProjectRepositoryId(repo.getId()).filter(RepositoryVerificationProfile::isEnabled).ifPresent(p->{
            add(checks,"LINT",p.getLintCommand());add(checks,"COVERAGE",p.getCoverageCommand());add(checks,"CONTRACT",p.getContractCommand());add(checks,"MIGRATION",p.getMigrationCommand());
        });
        return List.copyOf(checks);
    }
    private void add(List<Check> checks,String type,String command){if(command!=null&&!command.isBlank()){validateOptional(command);checks.add(new Check(type,command));}}
    private void validateOptional(String command){if(command!=null&&!command.isBlank())parser.parse(command);}

    public record VerificationProfileCommand(String lintCommand,String coverageCommand,String contractCommand,String migrationCommand,boolean enabled){}
    public record VerificationResult(VerificationRun run,List<VerificationRunItem> items){}
    private record Check(String type,String command){}
}
