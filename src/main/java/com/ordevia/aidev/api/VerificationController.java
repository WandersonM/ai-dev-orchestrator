package com.ordevia.aidev.api;

import com.ordevia.aidev.verification.application.VerificationService;
import com.ordevia.aidev.verification.domain.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class VerificationController {
    private final VerificationService verification;
    public VerificationController(VerificationService verification){this.verification=verification;}

    @GetMapping("/projects/{projectId}/repositories/{repositoryId}/verification")
    public Optional<RepositoryVerificationProfile> profile(@PathVariable UUID projectId,@PathVariable UUID repositoryId){return verification.profile(projectId,repositoryId);}

    @PutMapping("/projects/{projectId}/repositories/{repositoryId}/verification")
    public RepositoryVerificationProfile configure(@PathVariable UUID projectId,@PathVariable UUID repositoryId,@Valid @RequestBody VerificationRequest request){
        return verification.configure(projectId,repositoryId,new VerificationService.VerificationProfileCommand(request.lintCommand(),request.coverageCommand(),request.contractCommand(),request.migrationCommand(),request.enabled()));
    }

    @PostMapping("/work-items/{workItemId}/verify") public VerificationService.VerificationResult run(@PathVariable UUID workItemId){return verification.run(workItemId);}
    @GetMapping("/work-items/{workItemId}/verification-runs") public List<VerificationRun> runs(@PathVariable UUID workItemId){return verification.runs(workItemId);}
    @GetMapping("/verification-runs/{runId}/items") public List<VerificationRunItem> items(@PathVariable UUID runId){return verification.items(runId);}

    public record VerificationRequest(String lintCommand,String coverageCommand,String contractCommand,String migrationCommand,boolean enabled){}
}
