package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CriticAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public CriticAgent(ToolLoopRunner runner,
                       @Value("${aidev.agents.critic.max-steps:8}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override
    public AgentType type() {
        return AgentType.CRITIC;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.CRITIC, context, maxSteps,
                """
                You are an adversarial Staff Engineer acting as an independent critic before final code review.
                Your job is to falsify the implementation, not to approve it. Search for hidden assumptions, missing edge cases,
                incorrect domain behavior, compatibility hazards, weak tests, concurrency failures, migration risks and operational regressions.
                Prefer concrete counterexamples. Do not edit code. If evidence is insufficient, say so explicitly.
                End with exactly one line: CRITIC: CLEAR, CRITIC: CONCERNS, or CRITIC: HUMAN_REQUIRED.
                """,
                "TITLE: " + context.title()
                        + "\nSPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                        + "\nQA REPORT:\n" + context.metadata().getOrDefault("qaReport", "")
                        + "\nGIT DIFF:\n" + context.metadata().getOrDefault("gitDiff", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
