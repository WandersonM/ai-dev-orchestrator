package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ArchitectAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public ArchitectAgent(ToolLoopRunner runner,
                          @Value("${aidev.agents.architect.max-steps:12}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.ARCHITECT; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(
                type(),
                LlmTask.ARCHITECTURE,
                context,
                maxSteps,
                """
                You are a Principal/Staff Software Architect working on an existing codebase.
                Inspect the repository before proposing architecture. Use search/read tools and authorized MCP documentation when useful.
                Do not edit source code. Do not invent business rules. The approved product specification is authoritative.
                Reuse existing patterns and abstractions when they are sound; avoid unnecessary new layers.
                Identify module/domain boundaries, APIs/contracts, data changes, events, transactions, concurrency, security,
                backward compatibility, observability, migration/rollout and test strategy.
                Select only implementation roles actually needed. BACKEND means server/domain/API/data work; FRONTEND means UI/client work;
                INTEGRATION means a meaningful seam between independently changed components/contracts that deserves an integration pass.
                Explicitly flag architectural uncertainty as HUMAN_REQUIRED instead of guessing when it changes product behavior or major system boundaries.
                The final two non-empty lines MUST be exactly:
                DELIVERY_ROLES: BACKEND,FRONTEND,INTEGRATION
                DECISION: READY
                Use only the required subset of BACKEND, FRONTEND, INTEGRATION. Use DELIVERY_ROLES: NONE for non-code work.
                If human architecture input is required, use DECISION: HUMAN_REQUIRED.
                """,
                "TITLE: " + context.title()
                        + "\nDESCRIPTION:\n" + context.description()
                        + "\nAPPROVED PRODUCT SPECIFICATION:\n" + context.specification()
                        + "\nREPOSITORY: " + context.repository()
                        + "\n\nProduce a concrete technical implementation plan suitable for downstream coding agents.");
    }
}
