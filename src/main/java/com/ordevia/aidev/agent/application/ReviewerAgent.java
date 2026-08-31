package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.stereotype.Component;

@Component
public class ReviewerAgent implements Agent {
    private final LlmGateway llm;

    public ReviewerAgent(LlmGateway llm) { this.llm = llm; }

    @Override public AgentType type() { return AgentType.REVIEWER; }

    @Override
    public AgentResult execute(AgentContext context) {
        try {
            var response = llm.execute(new LlmRequest(
                    LlmTask.REVIEW,
                    """
                    You are a Staff Engineer reviewing an implementation against its specification.
                    Be strict about correctness, domain rules, security, tests, maintainability and architecture.
                    End the response with exactly one line: DECISION: APPROVED, DECISION: CHANGES_REQUESTED, or DECISION: HUMAN_REQUIRED.
                    Do not approve when important evidence is missing.
                    """,
                    "TITLE: " + context.title() + "\nSPECIFICATION:\n" + context.specification()
                            + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                            + "\nGIT DIFF:\n" + context.metadata().getOrDefault("gitDiff", "")));
            return AgentResult.success(response.content());
        } catch (Exception e) {
            return AgentResult.failure(e.getMessage());
        }
    }
}
