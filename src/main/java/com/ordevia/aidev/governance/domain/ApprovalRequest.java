package com.ordevia.aidev.governance.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="approval_request")
public class ApprovalRequest {
    @Id private UUID id;
    @Column(name="work_item_id",nullable=false) private UUID workItemId;
    @Column(name="session_id",nullable=false) private UUID sessionId;
    @Column(name="step_number",nullable=false) private int stepNumber;
    @Column(name="tool_name",nullable=false,length=160) private String toolName;
    @Column(name="arguments_hash",nullable=false,length=128) private String argumentsHash;
    @Enumerated(EnumType.STRING) @Column(name="risk_level",nullable=false,length=30) private RiskLevel riskLevel;
    @Column(nullable=false,length=500) private String capabilities;
    @Column(columnDefinition="text") private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ApprovalStatus status;
    @Column(name="requested_at",nullable=false) private Instant requestedAt;
    @Column(name="decided_at") private Instant decidedAt;
    @Column(name="decided_by",length=200) private String decidedBy;
    @Column(name="decision_note",columnDefinition="text") private String decisionNote;

    protected ApprovalRequest() {}

    public ApprovalRequest(UUID id,UUID workItemId,UUID sessionId,int stepNumber,String toolName,String argumentsHash,
                           RiskLevel riskLevel,String capabilities,String reason){
        this.id=id;this.workItemId=workItemId;this.sessionId=sessionId;this.stepNumber=stepNumber;this.toolName=toolName;
        this.argumentsHash=argumentsHash;this.riskLevel=riskLevel;this.capabilities=capabilities;this.reason=reason;
        this.status=ApprovalStatus.PENDING;this.requestedAt=Instant.now();
    }

    public void approve(String by,String note){requirePending();status=ApprovalStatus.APPROVED;decidedBy=by;decisionNote=note;decidedAt=Instant.now();}
    public void reject(String by,String note){requirePending();status=ApprovalStatus.REJECTED;decidedBy=by;decisionNote=note;decidedAt=Instant.now();}
    private void requirePending(){if(status!=ApprovalStatus.PENDING)throw new IllegalStateException("Approval request already decided: "+status);}

    public UUID getId(){return id;} public UUID getWorkItemId(){return workItemId;} public UUID getSessionId(){return sessionId;}
    public int getStepNumber(){return stepNumber;} public String getToolName(){return toolName;} public String getArgumentsHash(){return argumentsHash;}
    public RiskLevel getRiskLevel(){return riskLevel;} public String getCapabilities(){return capabilities;} public String getReason(){return reason;}
    public ApprovalStatus getStatus(){return status;} public Instant getRequestedAt(){return requestedAt;} public Instant getDecidedAt(){return decidedAt;}
    public String getDecidedBy(){return decidedBy;} public String getDecisionNote(){return decisionNote;}
}
