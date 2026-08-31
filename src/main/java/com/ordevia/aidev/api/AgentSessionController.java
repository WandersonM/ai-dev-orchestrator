package com.ordevia.aidev.api;

import com.ordevia.aidev.session.application.AgentSessionService;
import com.ordevia.aidev.session.domain.AgentCheckpoint;
import com.ordevia.aidev.session.domain.AgentSession;
import com.ordevia.aidev.session.domain.AgentSessionMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AgentSessionController {
    private final AgentSessionService sessions;

    public AgentSessionController(AgentSessionService sessions) { this.sessions = sessions; }

    @GetMapping("/work-items/{workItemId}/agent-sessions")
    public List<AgentSession> byWorkItem(@PathVariable UUID workItemId) { return sessions.list(workItemId); }

    @GetMapping("/agent-sessions/{id}")
    public AgentSession get(@PathVariable UUID id) { return sessions.get(id); }

    @GetMapping("/agent-sessions/{id}/messages")
    public List<AgentSessionMessage> messages(@PathVariable UUID id) { return sessions.messages(id); }

    @GetMapping("/agent-sessions/{id}/checkpoints")
    public List<AgentCheckpoint> checkpoints(@PathVariable UUID id) { return sessions.checkpoints(id); }

    @PostMapping("/agent-sessions/{id}/pause")
    public AgentSession pause(@PathVariable UUID id) { return sessions.requestPause(id); }

    @PostMapping("/agent-sessions/{id}/resume")
    public AgentSession resume(@PathVariable UUID id) { return sessions.resume(id); }

    @PostMapping("/agent-sessions/{id}/cancel")
    public AgentSession cancel(@PathVariable UUID id) { return sessions.requestCancel(id); }

    @PostMapping("/agent-sessions/{id}/messages")
    public AgentSessionMessage message(@PathVariable UUID id, @Valid @RequestBody HumanMessageRequest request) {
        return sessions.addHumanMessage(id, request.content(), request.providedBy());
    }

    public record HumanMessageRequest(@NotBlank String content, String providedBy) {}
}
