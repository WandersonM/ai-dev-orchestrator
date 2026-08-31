package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewerAgent implements Agent {
    private final ToolLoopRunner runner;
    private final CriticAgent critic;
    private final int maxSteps;

    public ReviewerAgent(ToolLoopRunner runner,
                         CriticAgent critic,
                         @Value("${aidev.agents.review.max-steps:10}") int maxSteps) {
        this.runner = runner;
        this.critic = critic;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.REVIEWER; }

    @Override
    public AgentResult execute(AgentContext context) {
        AgentResult criticResult = critic.execute(context);
        if (!criticResult.success()) {
            return AgentResult.failure("Critic subagent failed: " + criticResult.error());
        }
        String criticReport = criticResult.output();
        if (criticReport != null && criticReport.contains("CRITIC: HUMAN_REQUIRED")) {
            return AgentResult.success(criticReport + "\n\nDECISION: HUMAN_REQUIRED");
        }

        return runner.run(type(), LlmTask.REVIEW, context, maxSteps,
                """
                You are a Staff Engineer performing the final read-only code review against an approved product specification and architecture plan.
                An independent adversarial Critic has already inspected the work. Treat its concerns as evidence to investigate, not as truth to rubber-stamp.
                Inspect repository context when the diff/report is insufficient. Be strict about correctness, domain invariants, concurrency,
                transactional behavior, tests, maintainability, backward compatibility and architecture. Do not edit source code.
                Separate blockers from suggestions and cite concrete files/symbols whenever possible.
                End with exactly one line: DECISION: APPROVED, DECISION: CHANGES_REQUESTED, or DECISION: HUMAN_REQUIRED.
                Do not approve when important evidence is missing or critic concerns remain unresolved.
                """,
                "TITLE: " + context.title()
                        + "\nSPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                        + "\nINTEGRATION REPORT:\n" + context.metadata().getOrDefault("integrationReport", "")
                        + "\nQA REPORT:\n" + context.metadata().getOrDefault("qaReport", "")
                        + "\nCRITIC REPORT:\n" + criticReport
                        + "\nGIT DIFF:\n" + context.metadata().getOrDefault("gitDiff", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
