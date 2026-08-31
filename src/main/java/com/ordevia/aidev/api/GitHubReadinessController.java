package com.ordevia.aidev.api;

import com.ordevia.aidev.github.application.GitHubReadinessService;
import com.ordevia.aidev.workflow.application.WorkflowEngine;
import com.ordevia.aidev.workitem.domain.WorkItem;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/work-items/{workItemId}/github-readiness")
public class GitHubReadinessController {
    private final GitHubReadinessService readiness;
    private final WorkflowEngine workflow;

    public GitHubReadinessController(GitHubReadinessService readiness,WorkflowEngine workflow){this.readiness=readiness;this.workflow=workflow;}

    @GetMapping public GitHubReadinessService.Readiness readiness(@PathVariable UUID workItemId){return readiness.workItem(workItemId);}

    @PostMapping("/complete-if-merged")
    public WorkItem completeIfMerged(@PathVariable UUID workItemId){
        var result=readiness.workItem(workItemId);
        if(!result.allMerged())throw new IllegalStateException("Not all coordinated pull requests are merged: "+result.blockers());
        return workflow.markDone(workItemId);
    }
}
