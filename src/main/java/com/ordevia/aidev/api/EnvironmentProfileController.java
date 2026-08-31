package com.ordevia.aidev.api;

import com.ordevia.aidev.execution.application.EnvironmentProfileService;
import com.ordevia.aidev.execution.domain.EnvironmentProfile;
import com.ordevia.aidev.execution.domain.ExecutionBackendType;
import com.ordevia.aidev.execution.domain.NetworkPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/repositories/{repositoryId}/environment")
public class EnvironmentProfileController {
    private final EnvironmentProfileService environments;

    public EnvironmentProfileController(EnvironmentProfileService environments) {
        this.environments = environments;
    }

    @GetMapping
    public EnvironmentProfile get(@PathVariable UUID projectId, @PathVariable UUID repositoryId) {
        return environments.find(projectId, repositoryId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Environment profile not found"));
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public EnvironmentProfile upsert(@PathVariable UUID projectId,
                                     @PathVariable UUID repositoryId,
                                     @Valid @RequestBody EnvironmentRequest request) {
        return environments.upsert(projectId, repositoryId, new EnvironmentProfileService.ProfileRequest(
                request.backendType(), request.containerImage(), request.networkPolicy(), request.cpuLimit(),
                request.memoryLimitMb(), request.pidsLimit(), request.timeoutSeconds(), request.setupCommand(),
                request.envAllowlist(), request.secretAllowlist()));
    }

    @PostMapping("/enable")
    public EnvironmentProfile enable(@PathVariable UUID projectId, @PathVariable UUID repositoryId) {
        return environments.setEnabled(projectId, repositoryId, true);
    }

    @PostMapping("/disable")
    public EnvironmentProfile disable(@PathVariable UUID projectId, @PathVariable UUID repositoryId) {
        return environments.setEnabled(projectId, repositoryId, false);
    }

    public record EnvironmentRequest(
            @NotNull ExecutionBackendType backendType,
            String containerImage,
            NetworkPolicy networkPolicy,
            @Min(1) double cpuLimit,
            @Min(128) int memoryLimitMb,
            @Min(32) int pidsLimit,
            @Min(10) @Max(7200) int timeoutSeconds,
            String setupCommand,
            List<String> envAllowlist,
            List<String> secretAllowlist
    ) {}
}
