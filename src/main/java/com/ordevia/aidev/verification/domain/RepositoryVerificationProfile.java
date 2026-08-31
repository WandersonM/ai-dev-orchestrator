package com.ordevia.aidev.verification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="repository_verification_profile")
public class RepositoryVerificationProfile {
    @Id private UUID id;
    @Column(name="project_repository_id",nullable=false,unique=true) private UUID projectRepositoryId;
    @Column(name="lint_command",columnDefinition="text") private String lintCommand;
    @Column(name="coverage_command",columnDefinition="text") private String coverageCommand;
    @Column(name="contract_command",columnDefinition="text") private String contractCommand;
    @Column(name="migration_command",columnDefinition="text") private String migrationCommand;
    @Column(nullable=false) private boolean enabled;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    protected RepositoryVerificationProfile() {}
    public RepositoryVerificationProfile(UUID id,UUID repoId,String lint,String coverage,String contract,String migration){
        this.id=id;this.projectRepositoryId=repoId;this.lintCommand=lint;this.coverageCommand=coverage;this.contractCommand=contract;this.migrationCommand=migration;
        this.enabled=true;this.createdAt=Instant.now();this.updatedAt=createdAt;
    }
    public void update(String lint,String coverage,String contract,String migration,boolean enabled){this.lintCommand=lint;this.coverageCommand=coverage;this.contractCommand=contract;this.migrationCommand=migration;this.enabled=enabled;this.updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getProjectRepositoryId(){return projectRepositoryId;} public String getLintCommand(){return lintCommand;}
    public String getCoverageCommand(){return coverageCommand;} public String getContractCommand(){return contractCommand;} public String getMigrationCommand(){return migrationCommand;}
    public boolean isEnabled(){return enabled;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
