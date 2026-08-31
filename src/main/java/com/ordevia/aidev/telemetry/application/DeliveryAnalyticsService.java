package com.ordevia.aidev.telemetry.application;

import com.ordevia.aidev.artifact.infrastructure.WorkItemArtifactJpaRepository;
import com.ordevia.aidev.governance.domain.ApprovalStatus;
import com.ordevia.aidev.governance.infrastructure.ApprovalRequestJpaRepository;
import com.ordevia.aidev.project.domain.WaveExecutionStatus;
import com.ordevia.aidev.project.infrastructure.ProjectJpaRepository;
import com.ordevia.aidev.project.infrastructure.WaveExecutionItemJpaRepository;
import com.ordevia.aidev.project.infrastructure.WaveExecutionJpaRepository;
import com.ordevia.aidev.session.domain.AgentSessionMessageRole;
import com.ordevia.aidev.session.infrastructure.AgentSessionJpaRepository;
import com.ordevia.aidev.session.infrastructure.AgentSessionMessageJpaRepository;
import com.ordevia.aidev.telemetry.infrastructure.LlmCallMetricJpaRepository;
import com.ordevia.aidev.verification.domain.VerificationStatus;
import com.ordevia.aidev.verification.infrastructure.VerificationRunJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;

@Service
public class DeliveryAnalyticsService {
    private final ProjectJpaRepository projects;
    private final WorkItemJpaRepository workItems;
    private final LlmCallMetricJpaRepository llmCalls;
    private final AgentSessionJpaRepository sessions;
    private final AgentSessionMessageJpaRepository messages;
    private final ApprovalRequestJpaRepository approvals;
    private final VerificationRunJpaRepository verifications;
    private final WorkItemArtifactJpaRepository artifacts;
    private final WaveExecutionJpaRepository waves;
    private final WaveExecutionItemJpaRepository waveItems;

    public DeliveryAnalyticsService(ProjectJpaRepository projects, WorkItemJpaRepository workItems,
                                    LlmCallMetricJpaRepository llmCalls, AgentSessionJpaRepository sessions,
                                    AgentSessionMessageJpaRepository messages, ApprovalRequestJpaRepository approvals,
                                    VerificationRunJpaRepository verifications, WorkItemArtifactJpaRepository artifacts,
                                    WaveExecutionJpaRepository waves, WaveExecutionItemJpaRepository waveItems) {
        this.projects=projects;this.workItems=workItems;this.llmCalls=llmCalls;this.sessions=sessions;this.messages=messages;
        this.approvals=approvals;this.verifications=verifications;this.artifacts=artifacts;this.waves=waves;this.waveItems=waveItems;
    }

    @Transactional(readOnly = true)
    public ProjectDeliverySummary project(UUID projectId) {
        if(!projects.existsById(projectId)) throw new NoSuchElementException("Project not found");
        List<WorkItem> items=workItems.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<WorkItemDeliveryMetric> metrics=items.stream().map(this::metric).toList();
        long done=metrics.stream().filter(m->m.status()==WorkItemStatus.DONE).count();
        long failed=metrics.stream().filter(m->m.status()==WorkItemStatus.FAILED).count();
        long human=metrics.stream().filter(WorkItemDeliveryMetric::humanIntervention).count();
        long firstPass=metrics.stream().filter(m->m.status()==WorkItemStatus.DONE&&m.reviewIterations()<=1).count();
        BigDecimal cost=metrics.stream().map(WorkItemDeliveryMetric::estimatedCostUsd).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(8,RoundingMode.HALF_UP);
        long tokens=metrics.stream().mapToLong(WorkItemDeliveryMetric::totalTokens).sum();
        long llmLatency=metrics.stream().mapToLong(WorkItemDeliveryMetric::llmLatencyMs).sum();
        double avgCycle=metrics.stream().filter(m->m.cycleTimeMs()!=null).mapToLong(WorkItemDeliveryMetric::cycleTimeMs).average().orElse(0);
        List<WaveDeliveryMetric> waveMetrics=waves.findByProjectIdOrderByStartedAtDesc(projectId).stream().map(w->{
            var wi=waveItems.findByWaveExecutionIdOrderByStartedAtAsc(w.getId());
            long wf=wi.stream().filter(i->i.getErrorMessage()!=null||i.getStatusAfter()==WorkItemStatus.FAILED).count();
            Long duration=w.getFinishedAt()==null?null:Duration.between(w.getStartedAt(),w.getFinishedAt()).toMillis();
            return new WaveDeliveryMetric(w.getId(),w.getStatus(),wi.size(),wf,duration,w.getStartedAt(),w.getFinishedAt());
        }).toList();
        return new ProjectDeliverySummary(projectId,items.size(),done,failed,human,ratio(human,items.size()),firstPass,ratio(firstPass,done),tokens,llmLatency,cost,Math.round(avgCycle),metrics,waveMetrics);
    }

    private WorkItemDeliveryMetric metric(WorkItem item) {
        var calls=llmCalls.findByWorkItemIdOrderByCreatedAtAsc(item.getId());
        BigDecimal cost=calls.stream().map(c->c.getEstimatedCostUsd()).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(8,RoundingMode.HALF_UP);
        long tokens=calls.stream().mapToLong(c->c.getTotalTokens()).sum();
        long latency=calls.stream().mapToLong(c->c.getLatencyMs()).sum();
        var agentSessions=sessions.findByWorkItemIdOrderByCreatedAtDesc(item.getId());
        long humanMessages=agentSessions.stream().flatMap(s->messages.findBySessionIdOrderByCreatedAtAsc(s.getId()).stream()).filter(m->m.getRole()==AgentSessionMessageRole.HUMAN).count();
        var itemApprovals=approvals.findByWorkItemIdOrderByRequestedAtDesc(item.getId());
        long approvalDecisions=itemApprovals.stream().filter(a->a.getStatus()!=ApprovalStatus.PENDING).count();
        boolean humanIntervention=humanMessages>0||approvalDecisions>0||humanGateStatus(item.getStatus());
        var verificationRuns=verifications.findByWorkItemIdOrderByStartedAtDesc(item.getId());
        long verificationFailures=verificationRuns.stream().filter(v->v.getStatus()==VerificationStatus.FAILED).count();
        long artifactCount=artifacts.findByWorkItemIdOrderByCreatedAtAsc(item.getId()).size();
        Long cycle=item.getPublishedAt()==null?null:Duration.between(item.getCreatedAt(),item.getPublishedAt()).toMillis();
        return new WorkItemDeliveryMetric(item.getId(),item.getExternalId(),item.getTitle(),item.getStatus(),item.getReviewIterations(),agentSessions.size(),humanMessages,approvalDecisions,humanIntervention,verificationRuns.size(),verificationFailures,artifactCount,tokens,latency,cost,cycle,item.getCreatedAt(),item.getPublishedAt());
    }

    private boolean humanGateStatus(WorkItemStatus status){return switch(status){
        case WAITING_FOR_USER_INPUT,READY_FOR_PLANNING_REVIEW,PLANNING_HUMAN_REQUIRED,DOMAIN_HUMAN_REQUIRED,ARCHITECTURE_HUMAN_REQUIRED,RELEASE_HUMAN_REQUIRED,READY_FOR_HUMAN_REVIEW -> true;
        default -> false;
    };}
    private double ratio(long value,long total){return total==0?0:BigDecimal.valueOf(value).divide(BigDecimal.valueOf(total),4,RoundingMode.HALF_UP).doubleValue();}

    public record ProjectDeliverySummary(UUID projectId,long workItems,long done,long failed,long humanInterventions,double humanInterventionRate,long firstPassDone,double firstPassReviewRate,long totalTokens,long llmLatencyMs,BigDecimal estimatedCostUsd,long averageCycleTimeMs,List<WorkItemDeliveryMetric> items,List<WaveDeliveryMetric> waves){}
    public record WorkItemDeliveryMetric(UUID workItemId,String externalId,String title,WorkItemStatus status,int reviewIterations,long agentSessions,long humanMessages,long approvalDecisions,boolean humanIntervention,long verificationRuns,long verificationFailures,long artifacts,long totalTokens,long llmLatencyMs,BigDecimal estimatedCostUsd,Long cycleTimeMs,java.time.Instant createdAt,java.time.Instant publishedAt){}
    public record WaveDeliveryMetric(UUID waveExecutionId,WaveExecutionStatus status,long workItems,long failures,Long durationMs,java.time.Instant startedAt,java.time.Instant finishedAt){}
}
