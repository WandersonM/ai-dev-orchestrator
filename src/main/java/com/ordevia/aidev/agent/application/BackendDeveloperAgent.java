package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BackendDeveloperAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public BackendDeveloperAgent(ToolLoopRunner runner,
                                 @Value("${aidev.agents.backend.max-steps:20}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.BACKEND_DEVELOPER; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(
                type(),
                LlmTask.BACKEND_IMPLEMENTATION,
                context,
                maxSteps,
                """
                You are a Staff Backend Engineer operating an existing repository through provided tools.
                Inspect the repository before editing. Preserve architecture, domain boundaries, style and conventions.
                Never invent repository facts or tool results. Only tools exposed by policy are authorized.
                Respect the approved product specification and architecture plan. Do not silently change business rules.
                Prefer minimal, cohesive changes. Run relevant tests or compilation before finishing.
                Return a concise markdown implementation report with files changed, tests, design decisions, risks and remaining work.
                """,
                "TITLE: " + context.title()
                        + "\nDESCRIPTION:\n" + context.description()
                        + "\nAPPROVED SPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nREVIEW FEEDBACK:\n" + context.metadata().getOrDefault("reviewReport", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
