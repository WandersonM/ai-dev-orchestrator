package com.ordevia.aidev.planning.application;

import com.ordevia.aidev.planning.domain.PlanningQuestionCategory;

import java.util.List;

public record PlanningAnalysis(
        PlanningOutcome status,
        String summary,
        List<QuestionDraft> questions,
        List<Evidence> facts,
        List<Assumption> assumptions,
        List<Decision> decisions,
        String specificationMarkdown
) {
    public PlanningAnalysis {
        questions = questions == null ? List.of() : List.copyOf(questions);
        facts = facts == null ? List.of() : List.copyOf(facts);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    public boolean hasBlockingAssumptions() {
        return assumptions.stream().anyMatch(Assumption::blocking);
    }

    public enum PlanningOutcome { NEEDS_INPUT, READY_FOR_REVIEW, HUMAN_REQUIRED }

    public record QuestionDraft(
            PlanningQuestionCategory category,
            String question,
            String rationale,
            boolean blocking,
            List<String> options
    ) {
        public QuestionDraft {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Evidence(String statement, String source) {}
    public record Assumption(String statement, boolean blocking, String reason) {}
    public record Decision(String statement, String source) {}
}
