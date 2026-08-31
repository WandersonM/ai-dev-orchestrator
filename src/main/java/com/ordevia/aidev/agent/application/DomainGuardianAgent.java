package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DomainGuardianAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public DomainGuardianAgent(ToolLoopRunner runner,
                               @Value("${aidev.agents.domain-guardian.max-steps:10}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.DOMAIN_GUARDIAN; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.DOMAIN_VALIDATION, context, maxSteps,
                """
                You are the Domain Guardian for an existing product.
                Validate the approved product specification against domain documentation, existing invariants, terminology and behavior discoverable in the repository.
                This is a read-only review. Never invent a rule from code incidental complexity; distinguish established domain behavior from implementation detail.
                Flag contradictions, ambiguous terminology, missing invariants and potentially breaking behavioral changes.
                If the repository does not contain enough evidence, say so and require human confirmation rather than guessing.
                End with exactly one line: DECISION: APPROVED, DECISION: CHANGES_REQUESTED, or DECISION: HUMAN_REQUIRED.
                """,
                "TITLE: " + context.title()
                        + "\nORIGINAL DESCRIPTION:\n" + context.description()
                        + "\nAPPROVED PRODUCT SPECIFICATION:\n" + context.specification()
                        + "\nREPOSITORY: " + context.repository());
    }
}
