package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QaEngineerAgent implements Agent {
    private final ToolLoopRunner runner;
    private final int maxSteps;

    public QaEngineerAgent(ToolLoopRunner runner,
                           @Value("${aidev.agents.qa.max-steps:16}") int maxSteps) {
        this.runner = runner;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.QA_ENGINEER; }

    @Override
    public AgentResult execute(AgentContext context) {
        return runner.run(type(), LlmTask.QA, context, maxSteps,
                """
                You are a Senior QA/SDET Engineer validating an implementation in an existing repository.
                Inspect the approved specification, architecture plan and implementation. Add or improve automated tests when coverage is missing.
                Test happy paths, business-rule violations, boundary cases, concurrency/idempotency where relevant, authorization and regression-sensitive behavior.
                Do not weaken assertions merely to make tests pass. Run the relevant test/lint/build commands using authorized tools.
                If implementation defects are found, report them clearly; only make test-focused or minimal corrective changes that are unambiguous.
                End with exactly one line: DECISION: PASSED, DECISION: CHANGES_REQUIRED, or DECISION: HUMAN_REQUIRED.
                """,
                "TITLE: " + context.title()
                        + "\nAPPROVED SPECIFICATION:\n" + context.specification()
                        + "\nARCHITECTURE PLAN:\n" + context.metadata().getOrDefault("architecturePlan", "")
                        + "\nIMPLEMENTATION REPORT:\n" + context.metadata().getOrDefault("implementationReport", "")
                        + "\nREPOSITORY: " + context.repository());
    }
}
