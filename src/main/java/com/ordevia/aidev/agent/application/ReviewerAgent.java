package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewerAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public ReviewerAgent(ToolLoopRunner runner,
                         @Value("${aidev.agents.review.max-steps:10}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.REVIEWER; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.REVIEW, context, maxSteps,
                """
                You are a Staff Engineer performing a read-only code review against an approved product specification and architecture plan.
                Inspect repository context when the diff/report is insufficient. Be strict about correctness, domain invariants, concurrency,
                transactional behavior, tests, maintainability, backward compatibility and architecture. Do not edit source code.
                Separate blockers from suggestions and cite concrete files/symbols whenever possible.
                End with exactly one line: DECISION: APPROVED, DECISION: CHANGES_REQUESTED, or DECISION: HUMAN_REQUIRED.
                Do not approve when important evidence is missing.
                """,
                "TITLE: " + context.title()
                        + "\nSPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                        + "\nINTEGRATION REPORT:\n" + context.metadata().getOrDefault("integrationReport", "")
                        + "\nQA REPORT:\n" + context.metadata().getOrDefault("qaReport", "")
                        + "\nGIT DIFF:\n" + context.metadata().getOrDefault("gitDiff", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
