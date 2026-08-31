package com.ordevia.aidev.api;

import com.ordevia.aidev.audit.application.AuditService;
import com.ordevia.aidev.audit.domain.AuditEvent;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/work-items/{workItemId}")
    public List<AuditEvent> byWorkItem(@PathVariable UUID workItemId) {
        return audit.byWorkItem(workItemId);
    }

    @GetMapping("/agent-sessions/{sessionId}")
    public List<AuditEvent> bySession(@PathVariable UUID sessionId) {
        return audit.bySession(sessionId);
    }
}
