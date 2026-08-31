package com.ordevia.aidev.session.application;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.session.domain.*;
import com.ordevia.aidev.session.infrastructure.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    public AgentSessionService(AgentSessionJpaRepository sessions,
                               AgentSessionMessageJpaRepository messages,
                               AgentCheckpointJpaRepository checkpoints) {
        this.sessions = sessions; this.messages = messages; this.checkpoints = checkpoints;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSession openOrResume(UUID workItemId, AgentType agentType) {
        AgentSession session = sessions.findFirstByWorkItemIdAndAgentTypeAndStatusInOrderByCreatedAtDesc(workItemId, agentType, ACTIVE)
                .orElseGet(() -> sessions.save(new AgentSession(UUID.randomUUID(), workItemId, agentType)));
        if (session.getStatus() == AgentSessionStatus.CREATED || session.getStatus() == AgentSessionStatus.PAUSED) session.start();
        sessions.save(session);
        checkpoint(session, AgentCheckpointType.SESSION_STARTED, session.getCurrentStep(), "Agent session active", null);
        return session;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSession requestPause(UUID id) { AgentSession s=required(id); s.requestPause(); return sessions.save(s); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSession resume(UUID id) { AgentSession s=required(id); s.resume(); checkpoint(s, AgentCheckpointType.RESUMED, s.getCurrentStep(), "Human resumed session", null); return sessions.save(s); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSession requestCancel(UUID id) { AgentSession s=required(id); s.requestCancel(); return sessions.save(s); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentSessionMessage addHumanMessage(UUID id, String content, String providedBy) {
        AgentSession s=required(id);
        if (s.getStatus().terminal()) throw new IllegalStateException("Cannot message a terminal session");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message cannot be blank");
        return messages.save(new AgentSessionMessage(UUID.randomUUID(), id, AgentSessionMessageRole.HUMAN, content.trim(), providedBy));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ControlSnapshot controlPoint(UUID sessionId, int step, AgentCheckpointType type, String summary, String providerTurnId) {
        AgentSession s=required(sessionId);
        s.step(step);
        if (s.getStatus() == AgentSessionStatus.CANCEL_REQUESTED) {
            s.markCancelled(); sessions.save(s); checkpoint(s, AgentCheckpointType.CANCELLED, step, "Cancellation acknowledged", providerTurnId);
            return new ControlSnapshot(s.getStatus(), List.of());
        }
        if (s.getStatus() == AgentSessionStatus.PAUSE_REQUESTED) {
            s.markPaused(); sessions.save(s); checkpoint(s, AgentCheckpointType.PAUSED, step, "Pause acknowledged at safe point", providerTurnId);
            return new ControlSnapshot(s.getStatus(), List.of());
        }
        s.heartbeat(); sessions.save(s); checkpoint(s, type, step, summary, providerTurnId);
        List<AgentSessionMessage> pending = messages.findBySessionIdAndRoleAndConsumedAtIsNullOrderByCreatedAtAsc(sessionId, AgentSessionMessageRole.HUMAN);
        List<String> human = new ArrayList<>();
        for (AgentSessionMessage message : pending) { human.add(message.getContent()); message.markConsumed(); }
        if (!pending.isEmpty()) {
            messages.saveAll(pending);
            checkpoint(s, AgentCheckpointType.HUMAN_MESSAGE_APPLIED, step, "Applied " + pending.size() + " human message(s)", providerTurnId);
        }
        return new ControlSnapshot(s.getStatus(), List.copyOf(human));
    }

    public void awaitResumeOrCancel(UUID sessionId) {
        while (true) {
            AgentSessionStatus status = sessions.findById(sessionId).orElseThrow().getStatus();
            if (status == AgentSessionStatus.RUNNING) return;
            if (status == AgentSessionStatus.CANCEL_REQUESTED || status == AgentSessionStatus.CANCELLED || status == AgentSessionStatus.FAILED) {
                throw new AgentSessionCancelledException("Agent session cancelled");
            }
            try { Thread.sleep(Duration.ofMillis(350)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AgentSessionCancelledException("Agent session interrupted"); }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id, int step, String summary) { AgentSession s=required(id); if (!s.getStatus().terminal()) { s.complete(); sessions.save(s); checkpoint(s, AgentCheckpointType.COMPLETED, step, summary, null); } }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, int step, String error) { AgentSession s=required(id); if (!s.getStatus().terminal()) { s.fail(error); sessions.save(s); checkpoint(s, AgentCheckpointType.FAILED, step, error, null); } }

    @Transactional(readOnly = true) public List<AgentSession> list(UUID workItemId) { return sessions.findByWorkItemIdOrderByCreatedAtDesc(workItemId); }
    @Transactional(readOnly = true) public AgentSession get(UUID id) { return required(id); }
    @Transactional(readOnly = true) public List<AgentSessionMessage> messages(UUID id) { required(id); return messages.findBySessionIdOrderByCreatedAtAsc(id); }
    @Transactional(readOnly = true) public List<AgentCheckpoint> checkpoints(UUID id) { required(id); return checkpoints.findBySessionIdOrderBySequenceNumberAsc(id); }

    private void checkpoint(AgentSession s, AgentCheckpointType type, int step, String summary, String providerTurnId) {
        int seq=s.nextCheckpointSequence(); sessions.save(s);
        checkpoints.save(new AgentCheckpoint(UUID.randomUUID(), s.getId(), seq, step, type, trim(summary, 8000), providerTurnId));
    }
    private String trim(String value,int max){ if(value==null)return null; return value.length()<=max?value:value.substring(0,max); }
    private AgentSession required(UUID id){ return sessions.findById(id).orElseThrow(() -> new NoSuchElementException("Agent session not found")); }

    public record ControlSnapshot(AgentSessionStatus status, List<String> humanMessages) {}
    public static final class AgentSessionCancelledException extends RuntimeException { public AgentSessionCancelledException(String message){super(message);} }
}
