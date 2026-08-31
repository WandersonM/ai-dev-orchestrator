package com.ordevia.aidev.project.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_repository", uniqueConstraints = @UniqueConstraint(name = "uk_project_repository_alias", columnNames = {"project_id", "alias"}))
public class ProjectRepository {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(nullable = false, length = 80) private String alias;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ProjectRepositoryKind kind;
    @Column(name = "repository_path", nullable = false, columnDefinition = "text") private String repositoryPath;
    @Column(name = "base_branch", nullable = false, length = 200) private String baseBranch;
    @Column(name = "branch_prefix", nullable = false, length = 80) private String branchPrefix;
    @Column(name = "instructions_path", columnDefinition = "text") private String instructionsPath;
    @Column(name = "build_command", columnDefinition = "text") private String buildCommand;
    @Column(name = "test_command", columnDefinition = "text") private String testCommand;
    @Column(name = "java_version", length = 30) private String javaVersion;
    @Column(name = "node_version", length = 30) private String nodeVersion;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ProjectRepository() {}

    public ProjectRepository(UUID id, UUID projectId, String alias, ProjectRepositoryKind kind, String repositoryPath,
                             String baseBranch, String branchPrefix, String instructionsPath,
                             String buildCommand, String testCommand, String javaVersion, String nodeVersion) {
        this.id = id;
        this.projectId = projectId;
        this.alias = alias;
        this.kind = kind;
        this.repositoryPath = repositoryPath;
        this.baseBranch = baseBranch == null || baseBranch.isBlank() ? "main" : baseBranch;
        this.branchPrefix = branchPrefix == null || branchPrefix.isBlank() ? "ai/" : branchPrefix;
        this.instructionsPath = instructionsPath;
        this.buildCommand = buildCommand;
        this.testCommand = testCommand;
        this.javaVersion = javaVersion;
        this.nodeVersion = nodeVersion;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void disable() { this.enabled = false; this.updatedAt = Instant.now(); }
    public void enable() { this.enabled = true; this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getAlias() { return alias; }
    public ProjectRepositoryKind getKind() { return kind; }
    public String getRepositoryPath() { return repositoryPath; }
    public String getBaseBranch() { return baseBranch; }
    public String getBranchPrefix() { return branchPrefix; }
    public String getInstructionsPath() { return instructionsPath; }
    public String getBuildCommand() { return buildCommand; }
    public String getTestCommand() { return testCommand; }
    public String getJavaVersion() { return javaVersion; }
    public String getNodeVersion() { return nodeVersion; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
