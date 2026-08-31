package com.ordevia.aidev.session;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.session.domain.AgentSession;
import com.ordevia.aidev.session.domain.AgentSessionStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentSessionLifecycleTest {
    @Test
    void pauseAndResumeAreCooperativeStates() {
        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), AgentType.BACKEND_DEVELOPER);
        session.start();
        assertEquals(AgentSessionStatus.RUNNING, session.getStatus());
        session.requestPause();
        assertEquals(AgentSessionStatus.PAUSE_REQUESTED, session.getStatus());
        session.markPaused();
        assertEquals(AgentSessionStatus.PAUSED, session.getStatus());
        session.resume();
        assertEquals(AgentSessionStatus.RUNNING, session.getStatus());
    }

    @Test
    void cancellationRequiresAcknowledgementAtSafePoint() {
        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), AgentType.QA_ENGINEER);
        session.start();
        session.requestCancel();
        assertEquals(AgentSessionStatus.CANCEL_REQUESTED, session.getStatus());
        session.markCancelled();
        assertEquals(AgentSessionStatus.CANCELLED, session.getStatus());
        assertTrue(session.getStatus().terminal());
    }

    @Test
    void completedSessionCannotBePaused() {
        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), AgentType.REVIEWER);
        session.start();
        session.complete();
        assertThrows(IllegalStateException.class, session::requestPause);
    }

    @Test
    void checkpointsAndStepsAreMonotonic() {
        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), AgentType.ARCHITECT);
        session.start();
        session.step(4);
        session.step(2);
        assertEquals(4, session.getCurrentStep());
        assertEquals(1, session.nextCheckpointSequence());
        assertEquals(2, session.nextCheckpointSequence());
    }
}
