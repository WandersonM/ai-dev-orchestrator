package com.ordevia.aidev.verification.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="verification_run")
public class VerificationRun {
    @Id private UUID id;
    @Column(name="work_item_id",nullable=false) private UUID workItemId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private VerificationStatus status;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="finished_at") private Instant finishedAt;
    @Column(name="duration_ms") private Long durationMs;

    protected VerificationRun() {}
    public VerificationRun(UUID id,UUID workItemId){this.id=id;this.workItemId=workItemId;this.status=VerificationStatus.RUNNING;this.startedAt=Instant.now();}
    public void finish(boolean passed){this.status=passed?VerificationStatus.PASSED:VerificationStatus.FAILED;this.finishedAt=Instant.now();this.durationMs=Duration.between(startedAt,finishedAt).toMillis();}
    public UUID getId(){return id;} public UUID getWorkItemId(){return workItemId;} public VerificationStatus getStatus(){return status;}
    public Instant getStartedAt(){return startedAt;} public Instant getFinishedAt(){return finishedAt;} public Long getDurationMs(){return durationMs;}
}
