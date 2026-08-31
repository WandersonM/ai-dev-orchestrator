package com.ordevia.aidev.session.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_workspace_snapshot")
public class AgentWorkspaceSnapshot {
    @Id private UUID id;
    @Column(name="checkpoint_id", nullable=false) private UUID checkpointId;
    @Column(name="session_id", nullable=false) private UUID sessionId;
    @Column(name="repository_alias", nullable=false, length=120) private String repositoryAlias;
    @Column(name="worktree_path", nullable=false, columnDefinition="text") private String worktreePath;
    @Column(name="head_sha", nullable=false, length=64) private String headSha;
    @Column(name="snapshot_commit_sha", nullable=false, length=64) private String snapshotCommitSha;
    @Column(name="branch_name", length=300) private String branchName;
    @Column(name="created_at", nullable=false) private Instant createdAt;

    protected AgentWorkspaceSnapshot() {}

    public AgentWorkspaceSnapshot(UUID id, UUID checkpointId, UUID sessionId, String repositoryAlias,
                                  String worktreePath, String headSha, String snapshotCommitSha, String branchName) {
        this.id=id; this.checkpointId=checkpointId; this.sessionId=sessionId; this.repositoryAlias=repositoryAlias;
        this.worktreePath=worktreePath; this.headSha=headSha; this.snapshotCommitSha=snapshotCommitSha;
        this.branchName=branchName; this.createdAt=Instant.now();
    }

    public UUID getId(){return id;} public UUID getCheckpointId(){return checkpointId;} public UUID getSessionId(){return sessionId;}
    public String getRepositoryAlias(){return repositoryAlias;} public String getWorktreePath(){return worktreePath;}
    public String getHeadSha(){return headSha;} public String getSnapshotCommitSha(){return snapshotCommitSha;}
    public String getBranchName(){return branchName;} public Instant getCreatedAt(){return createdAt;}
}
