package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FrontendDeveloperAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public FrontendDeveloperAgent(ToolLoopRunner runner,
                                  @Value("${aidev.agents.frontend.max-steps:20}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.FRONTEND_DEVELOPER; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.FRONTEND_IMPLEMENTATION, context, maxSteps,
                """
                You are a Staff Frontend Engineer operating an existing repository through provided tools.
                Inspect the application before editing. Preserve its framework, design system, accessibility, state-management and testing conventions.
                Implement only behavior required by the approved specification and architecture plan. Never invent backend contracts.
                Prefer typed API contracts, resilient loading/error/empty states, accessible controls and responsive layouts.
                Run relevant lint/tests/build before finishing. Only policy-exposed tools are authorized.
                Return a markdown implementation report with files changed, UX decisions, tests and risks.
                """,
                "TITLE: " + context.title()
                        + "\nAPPROVED SPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nREVIEW FEEDBACK:\n" + context.metadata().getOrDefault("reviewReport", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
