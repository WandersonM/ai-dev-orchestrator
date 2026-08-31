package com.ordevia.aidev.execution.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "environment_profile", uniqueConstraints = @UniqueConstraint(name = "uk_environment_profile_repository", columnNames = "project_repository_id"))
public class EnvironmentProfile {
    @Id private UUID id;
    @Column(name = "project_repository_id", nullable = false) private UUID projectRepositoryId;
    @Enumerated(EnumType.STRING) @Column(name = "backend_type", nullable = false, length = 40) private ExecutionBackendType backendType;
    @Column(name = "container_image", columnDefinition = "text") private String containerImage;
    @Enumerated(EnumType.STRING) @Column(name = "network_policy", nullable = false, length = 30) private NetworkPolicy networkPolicy;
    @Column(name = "cpu_limit", nullable = false) private double cpuLimit;
    @Column(name = "memory_limit_mb", nullable = false) private int memoryLimitMb;
    @Column(name = "pids_limit", nullable = false) private int pidsLimit;
    @Column(name = "timeout_seconds", nullable = false) private int timeoutSeconds;
    @Column(name = "setup_command", columnDefinition = "text") private String setupCommand;
    @Column(name = "env_allowlist", columnDefinition = "text") private String envAllowlist;
    @Column(name = "secret_allowlist", columnDefinition = "text") private String secretAllowlist;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EnvironmentProfile() {}

    public EnvironmentProfile(UUID id, UUID projectRepositoryId, ExecutionBackendType backendType, String containerImage,
                              NetworkPolicy networkPolicy, double cpuLimit, int memoryLimitMb, int pidsLimit,
                              int timeoutSeconds, String setupCommand, String envAllowlist, String secretAllowlist) {
        this.id = id;
        this.projectRepositoryId = projectRepositoryId;
        this.backendType = backendType == null ? ExecutionBackendType.LOCAL_WORKTREE : backendType;
        this.containerImage = containerImage;
        this.networkPolicy = networkPolicy == null ? NetworkPolicy.DENY : networkPolicy;
        this.cpuLimit = cpuLimit <= 0 ? 2.0 : cpuLimit;
        this.memoryLimitMb = memoryLimitMb <= 0 ? 2048 : memoryLimitMb;
        this.pidsLimit = pidsLimit <= 0 ? 256 : pidsLimit;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 600 : timeoutSeconds;
        this.setupCommand = setupCommand;
        this.envAllowlist = envAllowlist;
        this.secretAllowlist = secretAllowlist;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void disable() { enabled = false; updatedAt = Instant.now(); }
    public void enable() { enabled = true; updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getProjectRepositoryId() { return projectRepositoryId; }
    public ExecutionBackendType getBackendType() { return backendType; }
    public String getContainerImage() { return containerImage; }
    public NetworkPolicy getNetworkPolicy() { return networkPolicy; }
    public double getCpuLimit() { return cpuLimit; }
    public int getMemoryLimitMb() { return memoryLimitMb; }
    public int getPidsLimit() { return pidsLimit; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getSetupCommand() { return setupCommand; }
    public String getEnvAllowlist() { return envAllowlist; }
    public String getSecretAllowlist() { return secretAllowlist; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
