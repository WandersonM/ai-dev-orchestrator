package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.*;
import com.ordevia.aidev.execution.infrastructure.EnvironmentProfileJpaRepository;
import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.infrastructure.ProjectRepositoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class EnvironmentProfileService {
    private final EnvironmentProfileJpaRepository profiles;
    private final ProjectRepositoryJpaRepository repositories;

    public EnvironmentProfileService(EnvironmentProfileJpaRepository profiles,
                                     ProjectRepositoryJpaRepository repositories) {
        this.profiles = profiles;
        this.repositories = repositories;
    }

    @Transactional
    public EnvironmentProfile upsert(UUID projectId, UUID repositoryId, ProfileRequest request) {
        ProjectRepository repository = repositories.findById(repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Project repository not found"));
        if (!projectId.equals(repository.getProjectId())) {
            throw new IllegalArgumentException("Repository does not belong to project");
        }
        profiles.findByProjectRepositoryId(repositoryId).ifPresent(existing -> profiles.delete(existing));
        EnvironmentProfile profile = new EnvironmentProfile(
                UUID.randomUUID(), repositoryId, request.backendType(), request.containerImage(), request.networkPolicy(),
                request.cpuLimit(), request.memoryLimitMb(), request.pidsLimit(), request.timeoutSeconds(),
                request.setupCommand(), normalizeList(request.envAllowlist()), normalizeList(request.secretAllowlist()));
        return profiles.save(profile);
    }

    @Transactional(readOnly = true)
    public Optional<EnvironmentProfile> find(UUID projectId, UUID repositoryId) {
        ProjectRepository repository = repositories.findById(repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Project repository not found"));
        if (!projectId.equals(repository.getProjectId())) throw new IllegalArgumentException("Repository does not belong to project");
        return profiles.findByProjectRepositoryId(repositoryId);
    }

    @Transactional
    public EnvironmentProfile setEnabled(UUID projectId, UUID repositoryId, boolean enabled) {
        EnvironmentProfile profile = find(projectId, repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Environment profile not found"));
        if (enabled) profile.enable(); else profile.disable();
        return profiles.save(profile);
    }

    private String normalizeList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(String::trim).filter(v -> !v.isBlank()).distinct().sorted().collect(java.util.stream.Collectors.joining(","));
    }

    public record ProfileRequest(
            ExecutionBackendType backendType,
            String containerImage,
            NetworkPolicy networkPolicy,
            double cpuLimit,
            int memoryLimitMb,
            int pidsLimit,
            int timeoutSeconds,
            String setupCommand,
            java.util.List<String> envAllowlist,
            java.util.List<String> secretAllowlist
    ) {}
}
