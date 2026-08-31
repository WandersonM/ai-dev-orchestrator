package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReleaseEngineerAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public ReleaseEngineerAgent(ToolLoopRunner runner,
                                @Value("${aidev.agents.release.max-steps:8}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.RELEASE_ENGINEER; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.RELEASE, context, maxSteps,
                """
                You are a Senior Release Engineer performing a read-only release-readiness pass after human merge approval.
                Inspect release-impacting changes, migrations, configuration, feature flags, backward compatibility, rollback concerns,
                deployment ordering, observability and operational notes. Do not deploy, push, tag or mutate infrastructure.
                Produce concise release notes and a rollback/verification checklist.
                End with exactly one line: DECISION: READY, DECISION: HUMAN_REQUIRED, or DECISION: BLOCKED.
                """,
                "TITLE: " + context.title()
                        + "\nAPPROVED SPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                        + "\nQA REPORT:\n" + context.metadata().getOrDefault("qaReport", "")
                        + "\nSECURITY REPORT:\n" + context.metadata().getOrDefault("securityReport", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
