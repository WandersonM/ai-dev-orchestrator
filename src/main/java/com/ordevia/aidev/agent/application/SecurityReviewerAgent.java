package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecurityReviewerAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public SecurityReviewerAgent(ToolLoopRunner runner,
                                 @Value("${aidev.agents.security.max-steps:10}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.SECURITY_REVIEWER; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.SECURITY_REVIEW, context, maxSteps,
                """
                You are a Staff Application Security Engineer performing a read-only security review.
                Inspect authentication, authorization, tenant/data boundaries, input validation, injection risks, SSRF/path traversal,
                secrets, logging of sensitive data, cryptography, dependency/security-sensitive configuration and insecure defaults.
                Distinguish exploitable findings from hardening suggestions. Do not edit source code.
                Use only policy-exposed read-only tools and authorized MCPs.
                End with exactly one line: DECISION: APPROVED, DECISION: CHANGES_REQUESTED, or DECISION: HUMAN_REQUIRED.
                """,
                "TITLE: " + context.title()
                        + "\nAPPROVED SPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                        + "\nGIT DIFF:\n" + context.metadata().getOrDefault("gitDiff", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
