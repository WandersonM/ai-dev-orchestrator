package com.ordevia.aidev.verification.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="verification_run_item")
public class VerificationRunItem {
    @Id private UUID id;
    @Column(name="verification_run_id",nullable=false) private UUID verificationRunId;
    @Column(name="project_repository_id") private UUID projectRepositoryId;
    @Column(name="repository_alias",nullable=false,length=120) private String repositoryAlias;
    @Column(name="check_type",nullable=false,length=40) private String checkType;
    @Column(name="command_text",nullable=false,columnDefinition="text") private String commandText;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private VerificationStatus status;
    @Column(name="exit_code") private Integer exitCode;
    @Column(name="output_text",columnDefinition="text") private String outputText;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="finished_at") private Instant finishedAt;
    @Column(name="duration_ms") private Long durationMs;

    protected VerificationRunItem() {}
    public VerificationRunItem(UUID id,UUID runId,UUID repoId,String alias,String type,String command){this.id=id;this.verificationRunId=runId;this.projectRepositoryId=repoId;this.repositoryAlias=alias;this.checkType=type;this.commandText=command;this.status=VerificationStatus.RUNNING;this.startedAt=Instant.now();}
    public void finish(int exitCode,String output){this.exitCode=exitCode;this.outputText=trim(output,30000);this.status=exitCode==0?VerificationStatus.PASSED:VerificationStatus.FAILED;this.finishedAt=Instant.now();this.durationMs=Duration.between(startedAt,finishedAt).toMillis();}
    private String trim(String value,int max){if(value==null)return null;return value.length()<=max?value:value.substring(0,max)+"\n...[truncated]";}
    public UUID getId(){return id;} public UUID getVerificationRunId(){return verificationRunId;} public UUID getProjectRepositoryId(){return projectRepositoryId;}
    public String getRepositoryAlias(){return repositoryAlias;} public String getCheckType(){return checkType;} public String getCommandText(){return commandText;}
    public VerificationStatus getStatus(){return status;} public Integer getExitCode(){return exitCode;} public String getOutputText(){return outputText;}
    public Instant getStartedAt(){return startedAt;} public Instant getFinishedAt(){return finishedAt;} public Long getDurationMs(){return durationMs;}
}
