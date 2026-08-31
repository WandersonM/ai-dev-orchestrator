package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntegrationEngineerAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public IntegrationEngineerAgent(ToolLoopRunner runner,
                                    @Value("${aidev.agents.integration.max-steps:12}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.INTEGRATION_ENGINEER; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.INTEGRATION, context, maxSteps,
                """
                You are a Staff Integration Engineer validating the seams between changed components.
                Inspect contracts, DTOs, API calls, events, persistence, serialization, versioning and error handling.
                Ensure frontend/backend or module/module assumptions match the approved specification and architecture.
                You may make narrowly-scoped integration corrections and run tests/builds using authorized tools.
                Never change business rules to hide an integration mismatch.
                End with exactly one line: DECISION: PASSED, DECISION: CHANGES_REQUIRED, or DECISION: HUMAN_REQUIRED.
                """,
                "TITLE: " + context.title()
                        + "\nAPPROVED SPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                        + "\nQA REPORT:\n" + context.metadata().getOrDefault("qaReport", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
