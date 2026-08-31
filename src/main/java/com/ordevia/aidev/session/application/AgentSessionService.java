package com.ordevia.aidev.session.application;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.execution.domain.ToolExecution;
import com.ordevia.aidev.execution.infrastructure.ToolExecutionJpaRepository;
import com.ordevia.aidev.session.domain.*;
import com.ordevia.aidev.session.infrastructure.*;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
public class AgentSessionService {
    private static final List<AgentSessionStatus> ACTIVE = List.of(
            AgentSessionStatus.CREATED, AgentSessionStatus.RUNNING,
            AgentSessionStatus.PAUSE_REQUESTED, AgentSessionStatus.PAUSED,
            AgentSessionStatus.CANCEL_REQUESTED);

    private final AgentSessionJpaRepository sessions;
    private final AgentSessionMessageJpaRepository messages;
    private final AgentCheckpointJpaRepository checkpoints;
    private final AgentWorkspaceSnapshotJpaRepository snapshots;
    private final ToolExecutionJpaRepository toolExecutions;
    private final WorkItemJpaRepository workItems;
    private final WorkspaceSnapshotService workspaceSnapshots;

    public AgentSessionService(AgentSessionJpaRepository sessions,
                               AgentSessionMessageJpaRepository messages,
                               AgentCheckpointJpaRepository checkpoints,
                               AgentWorkspaceSnapshotJpaRepository snapshots,
                               ToolExecutionJpaRepository toolExecutions,
                               WorkItemJpaRepository workItems,
                               WorkspaceSnapshotService workspaceSnapshots) {
        this.sessions=sessions; this.messages=messages; this.checkpoints=checkpoints; this.snapshots=snapshots;
        this.toolExecutions=toolExecutions; this.workItems=workItems; this.workspaceSnapshots=workspaceSnapshots;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSession openOrResume(UUID workItemId, AgentType agentType) {
        AgentSession session=sessions.findFirstByWorkItemIdAndAgentTypeAndStatusInOrderByCreatedAtDesc(workItemId,agentType,ACTIVE)
                .orElseGet(()->sessions.save(new AgentSession(UUID.randomUUID(),workItemId,agentType)));
        if(session.getStatus()==AgentSessionStatus.CREATED||session.getStatus()==AgentSessionStatus.PAUSED)session.start();
        sessions.save(session); checkpoint(session,AgentCheckpointType.SESSION_STARTED,session.getCurrentStep(),"Agent session active",null); return session;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) public AgentSession requestPause(UUID id){AgentSession s=required(id);s.requestPause();return sessions.save(s);}
    @Transactional(propagation = Propagation.REQUIRES_NEW) public AgentSession resume(UUID id){AgentSession s=required(id);s.resume();checkpoint(s,AgentCheckpointType.RESUMED,s.getCurrentStep(),"Human resumed session",null);return sessions.save(s);}
    @Transactional(propagation = Propagation.REQUIRES_NEW) public AgentSession requestCancel(UUID id){AgentSession s=required(id);s.requestCancel();return sessions.save(s);}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSessionMessage addHumanMessage(UUID id,String content,String providedBy){AgentSession s=required(id);if(s.getStatus().terminal())throw new IllegalStateException("Cannot message a terminal session");if(content==null||content.isBlank())throw new IllegalArgumentException("Message cannot be blank");return messages.save(new AgentSessionMessage(UUID.randomUUID(),id,AgentSessionMessageRole.HUMAN,content.trim(),providedBy));}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ControlSnapshot controlPoint(UUID sessionId,int step,AgentCheckpointType type,String summary,String providerTurnId){
        AgentSession s=required(sessionId);s.step(step);
        if(s.getStatus()==AgentSessionStatus.CANCEL_REQUESTED){s.markCancelled();sessions.save(s);AgentCheckpoint cp=checkpoint(s,AgentCheckpointType.CANCELLED,step,"Cancellation acknowledged",providerTurnId);return new ControlSnapshot(s.getStatus(),List.of(),cp.getId());}
        if(s.getStatus()==AgentSessionStatus.PAUSE_REQUESTED){s.markPaused();sessions.save(s);AgentCheckpoint cp=checkpoint(s,AgentCheckpointType.PAUSED,step,"Pause acknowledged at safe point",providerTurnId);return new ControlSnapshot(s.getStatus(),List.of(),cp.getId());}
        s.heartbeat();sessions.save(s);AgentCheckpoint cp=checkpoint(s,type,step,summary,providerTurnId);
        List<AgentSessionMessage> pending=messages.findBySessionIdAndRoleAndConsumedAtIsNullOrderByCreatedAtAsc(sessionId,AgentSessionMessageRole.HUMAN);List<String> human=new ArrayList<>();
        for(AgentSessionMessage message:pending){human.add(message.getContent());message.markConsumed();}
        if(!pending.isEmpty()){messages.saveAll(pending);checkpoint(s,AgentCheckpointType.HUMAN_MESSAGE_APPLIED,step,"Applied "+pending.size()+" human message(s)",providerTurnId);}
        return new ControlSnapshot(s.getStatus(),List.copyOf(human),cp.getId());
    }

    public void awaitResumeOrCancel(UUID sessionId){while(true){AgentSessionStatus status=sessions.findById(sessionId).orElseThrow().getStatus();if(status==AgentSessionStatus.RUNNING)return;if(status==AgentSessionStatus.CANCEL_REQUESTED||status==AgentSessionStatus.CANCELLED||status==AgentSessionStatus.FAILED)throw new AgentSessionCancelledException("Agent session cancelled");try{Thread.sleep(Duration.ofMillis(350));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AgentSessionCancelledException("Agent session interrupted");}}}

    @Transactional(propagation = Propagation.REQUIRES_NEW) public void complete(UUID id,int step,String summary){AgentSession s=required(id);if(!s.getStatus().terminal()){s.complete();sessions.save(s);checkpoint(s,AgentCheckpointType.COMPLETED,step,summary,null);}}
    @Transactional(propagation = Propagation.REQUIRES_NEW) public void fail(UUID id,int step,String error){AgentSession s=required(id);if(!s.getStatus().terminal()){s.fail(error);sessions.save(s);checkpoint(s,AgentCheckpointType.FAILED,step,error,null);}}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSession fork(UUID sourceSessionId, UUID checkpointId, String instruction, String providedBy){
        AgentSession source=required(sourceSessionId);
        if(!source.getStatus().terminal())throw new IllegalStateException("Cancel or finish the source session before forking it");
        AgentCheckpoint checkpoint=checkpoints.findById(checkpointId).orElseThrow(()->new NoSuchElementException("Checkpoint not found"));
        if(!checkpoint.getSessionId().equals(sourceSessionId))throw new IllegalArgumentException("Checkpoint does not belong to source session");
        if(!snapshots.existsByCheckpointId(checkpointId))throw new IllegalStateException("Checkpoint is not restorable because it has no workspace snapshot");
        WorkItem item=workItems.findById(source.getWorkItemId()).orElseThrow(()->new NoSuchElementException("WorkItem not found"));
        if(item.getStatus()==WorkItemStatus.DONE||item.getPublishedAt()!=null)throw new IllegalStateException("Cannot fork a completed or already published WorkItem");
        UUID newId=UUID.randomUUID(); Path forkRoot=workspaceSnapshots.createForkWorkspace(checkpointId,newId,Objects.toString(item.getExternalId(),item.getId().toString()));
        AgentSession fork=new AgentSession(newId,item.getId(),source.getAgentType(),source.getId(),checkpointId,source.getAttemptNumber()+1,checkpoint.getStepNumber(),forkRoot.toString());
        sessions.save(fork); item.setActiveWorkspacePath(forkRoot.toString()); item.moveTo(retryStatus(source.getAgentType())); workItems.save(item);
        if(instruction!=null&&!instruction.isBlank())messages.save(new AgentSessionMessage(UUID.randomUUID(),fork.getId(),AgentSessionMessageRole.HUMAN,instruction.trim(),providedBy));
        checkpoint(fork,AgentCheckpointType.SESSION_STARTED,fork.getCurrentStep(),"Forked from session "+sourceSessionId+" checkpoint "+checkpointId,null);
        return fork;
    }

    @Transactional(readOnly = true)
    public List<ToolExecution> toolHistory(UUID sessionId){return historyUntil(required(sessionId),Integer.MAX_VALUE,new HashSet<>());}

    private List<ToolExecution> historyUntil(AgentSession session,int maxStep,Set<UUID> visited){
        if(!visited.add(session.getId()))throw new IllegalStateException("Cycle detected in agent session lineage");
        List<ToolExecution> result=new ArrayList<>();
        if(session.getParentSessionId()!=null&&session.getForkedFromCheckpointId()!=null){
            AgentCheckpoint cp=checkpoints.findById(session.getForkedFromCheckpointId()).orElseThrow(()->new IllegalStateException("Fork checkpoint missing"));
            AgentSession parent=required(session.getParentSessionId()); result.addAll(historyUntil(parent,cp.getStepNumber(),visited));
        }
        result.addAll(toolExecutions.findBySessionIdAndStepNumberLessThanEqualOrderByStepNumberAsc(session.getId(),maxStep)); return List.copyOf(result);
    }

    @Transactional(readOnly = true) public List<AgentSession> list(UUID workItemId){return sessions.findByWorkItemIdOrderByCreatedAtDesc(workItemId);}
    @Transactional(readOnly = true) public AgentSession get(UUID id){return required(id);}
    @Transactional(readOnly = true) public List<AgentSessionMessage> messages(UUID id){required(id);return messages.findBySessionIdOrderByCreatedAtAsc(id);}
    @Transactional(readOnly = true) public List<AgentCheckpoint> checkpoints(UUID id){required(id);return checkpoints.findBySessionIdOrderBySequenceNumberAsc(id);}
    @Transactional(readOnly = true) public List<AgentWorkspaceSnapshot> snapshots(UUID id){required(id);return snapshots.findBySessionIdOrderByCreatedAtAsc(id);}

    private AgentCheckpoint checkpoint(AgentSession s,AgentCheckpointType type,int step,String summary,String providerTurnId){int seq=s.nextCheckpointSequence();sessions.save(s);return checkpoints.save(new AgentCheckpoint(UUID.randomUUID(),s.getId(),seq,step,type,trim(summary,8000),providerTurnId));}
    private String trim(String value,int max){if(value==null)return null;return value.length()<=max?value:value.substring(0,max);}
    private AgentSession required(UUID id){return sessions.findById(id).orElseThrow(()->new NoSuchElementException("Agent session not found"));}

    private WorkItemStatus retryStatus(AgentType type){return switch(type){
        case DOMAIN_GUARDIAN -> WorkItemStatus.READY_FOR_DOMAIN_VALIDATION;
        case ARCHITECT -> WorkItemStatus.READY_FOR_ARCHITECTURE;
        case BACKEND_DEVELOPER, FRONTEND_DEVELOPER -> WorkItemStatus.READY_FOR_DEVELOPMENT;
        case INTEGRATION_ENGINEER -> WorkItemStatus.INTEGRATING;
        case QA_ENGINEER -> WorkItemStatus.QA_VALIDATING;
        case REVIEWER -> WorkItemStatus.REVIEWING;
        case SECURITY_REVIEWER -> WorkItemStatus.SECURITY_REVIEWING;
        case RELEASE_ENGINEER -> WorkItemStatus.RELEASE_PREPARING;
        default -> throw new IllegalStateException("Agent type does not support execution fork: "+type);
    };}

    public record ControlSnapshot(AgentSessionStatus status,List<String> humanMessages,UUID checkpointId){}
    public static final class AgentSessionCancelledException extends RuntimeException{public AgentSessionCancelledException(String message){super(message);}}
}
