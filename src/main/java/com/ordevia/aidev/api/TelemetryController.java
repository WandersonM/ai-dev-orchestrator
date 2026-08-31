package com.ordevia.aidev.api;

import com.ordevia.aidev.governance.application.WorkItemBudgetService;
import com.ordevia.aidev.telemetry.application.LlmTelemetryService;
import com.ordevia.aidev.telemetry.domain.LlmCallMetric;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {
    private final LlmTelemetryService telemetry;
    private final WorkItemBudgetService budgets;

    public TelemetryController(LlmTelemetryService telemetry,WorkItemBudgetService budgets){this.telemetry=telemetry;this.budgets=budgets;}

    @GetMapping("/work-items/{id}/summary") public LlmTelemetryService.Summary workItemSummary(@PathVariable UUID id){return telemetry.workItemSummary(id);}
    @GetMapping("/work-items/{id}/calls") public List<LlmCallMetric> workItemCalls(@PathVariable UUID id){return telemetry.byWorkItem(id);}
    @GetMapping("/work-items/{id}/budget") public WorkItemBudgetService.BudgetStatus budget(@PathVariable UUID id){return budgets.status(id);}
    @GetMapping("/sessions/{id}/summary") public LlmTelemetryService.Summary sessionSummary(@PathVariable UUID id){return telemetry.sessionSummary(id);}
    @GetMapping("/sessions/{id}/calls") public List<LlmCallMetric> sessionCalls(@PathVariable UUID id){return telemetry.bySession(id);}
}
