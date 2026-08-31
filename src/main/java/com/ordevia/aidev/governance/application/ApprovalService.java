package com.ordevia.aidev.governance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.audit.application.AuditService;
import com.ordevia.aidev.governance.domain.*;
import com.ordevia.aidev.governance.infrastructure.ApprovalRequestJpaRepository;
import com.ordevia.aidev.session.application.AgentSessionService;
import com.ordevia.aidev.session.domain.AgentSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ApprovalService {
    private final ApprovalRequestJpaRepository approvals;
    private final AgentSessionService sessions;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public ApprovalService(ApprovalRequestJpaRepository approvals, AgentSessionService sessions, ObjectMapper mapper, AuditService audit) {
        this.approvals=approvals; this.sessions=sessions; this.mapper=mapper; this.audit=audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApprovalRequest require(UUID workItemId, UUID sessionId, int step, String toolName, Map<String,Object> arguments,
                                   ToolRiskAssessmentService.RiskAssessment assessment) {
        String hash=hash(arguments);
        ApprovalRequest request = approvals.findBySessionIdAndStepNumberAndToolNameAndArgumentsHash(sessionId,step,toolName,hash)
                .orElseGet(() -> approvals.save(new ApprovalRequest(UUID.randomUUID(),workItemId,sessionId,step,toolName,hash,
                        assessment.riskLevel(), assessment.capabilities().toString(), assessment.reason())));
        audit.append(workItemId, sessionId, "APPROVAL_REQUIRED", "SYSTEM", null,
                "ApprovalRequest", request.getId().toString(), Map.of(
                        "tool", toolName,
                        "step", step,
                        "risk", assessment.riskLevel().name(),
                        "capabilities", assessment.capabilities().toString(),
                        "reason", Objects.toString(assessment.reason(), "")));
        return request;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApprovalRequest approve(UUID id,String decidedBy,String note) {
        ApprovalRequest request=required(id); request.approve(decidedBy,note); approvals.save(request);
        audit.append(request.getWorkItemId(), request.getSessionId(), "APPROVAL_APPROVED", "HUMAN", decidedBy,
                "ApprovalRequest", request.getId().toString(), Map.of("note", Objects.toString(note, "")));
        resumeIfPaused(request.getSessionId()); return request;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApprovalRequest reject(UUID id,String decidedBy,String note) {
        ApprovalRequest request=required(id); request.reject(decidedBy,note); approvals.save(request);
        audit.append(request.getWorkItemId(), request.getSessionId(), "APPROVAL_REJECTED", "HUMAN", decidedBy,
                "ApprovalRequest", request.getId().toString(), Map.of("note", Objects.toString(note, "")));
        sessions.requestCancel(request.getSessionId()); return request;
    }

    @Transactional(readOnly = true) public ApprovalRequest get(UUID id){return required(id);}
    @Transactional(readOnly = true) public List<ApprovalRequest> pending(){return approvals.findByStatusOrderByRequestedAtAsc(ApprovalStatus.PENDING);}
    @Transactional(readOnly = true) public List<ApprovalRequest> byWorkItem(UUID id){return approvals.findByWorkItemIdOrderByRequestedAtDesc(id);}

    public void ensureApproved(UUID id) {
        ApprovalStatus status=approvals.findById(id).orElseThrow().getStatus();
        if(status==ApprovalStatus.REJECTED)throw new SecurityException("Human rejected approval request "+id);
        if(status!=ApprovalStatus.APPROVED)throw new IllegalStateException("Approval request is not approved: "+status);
    }

    private void resumeIfPaused(UUID sessionId) {
        AgentSessionStatus status=sessions.get(sessionId).getStatus();
        if(status==AgentSessionStatus.PAUSED||status==AgentSessionStatus.PAUSE_REQUESTED)sessions.resume(sessionId);
    }

    private ApprovalRequest required(UUID id){return approvals.findById(id).orElseThrow(()->new NoSuchElementException("Approval request not found"));}
    private String hash(Map<String,Object> args){
        try {
            byte[] json=mapper.writeValueAsBytes(new TreeMap<>(args)); byte[] digest=MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch(Exception e){throw new IllegalStateException("Unable to hash approval arguments",e);}
    }
}
