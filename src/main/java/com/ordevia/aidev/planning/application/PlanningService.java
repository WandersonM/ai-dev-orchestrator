package com.ordevia.aidev.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.execution.domain.AgentExecution;
import com.ordevia.aidev.execution.infrastructure.AgentExecutionJpaRepository;
import com.ordevia.aidev.planning.domain.*;
import com.ordevia.aidev.planning.infrastructure.PlanningQuestionJpaRepository;
import com.ordevia.aidev.planning.infrastructure.PlanningSessionJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PlanningService {
    private final WorkItemJpaRepository workItems;
    private final PlanningSessionJpaRepository sessions;
    private final PlanningQuestionJpaRepository questions;
    private final AgentExecutionJpaRepository executions;
    private final ProductPlanningAgent agent;
    private final ObjectMapper mapper;
    private final int maxRounds;

    public PlanningService(WorkItemJpaRepository workItems,
                           PlanningSessionJpaRepository sessions,
                           PlanningQuestionJpaRepository questions,
                           AgentExecutionJpaRepository executions,
                           ProductPlanningAgent agent,
                           ObjectMapper mapper,
                           @Value("${aidev.agents.planning.max-rounds:3}") int maxRounds) {
        this.workItems = workItems;
        this.sessions = sessions;
        this.questions = questions;
        this.executions = executions;
        this.agent = agent;
        this.mapper = mapper;
        this.maxRounds = Math.max(1, maxRounds);
    }

    @Transactional
    public PlanningView start(UUID workItemId) {
        WorkItem item = requiredWorkItem(workItemId);
        if (item.getStatus() != WorkItemStatus.NEW) {
            throw new IllegalStateException("Planning can only start from NEW; current status is " + item.getStatus());
        }
        PlanningSession session = sessions.findByWorkItemId(workItemId)
                .orElseGet(() -> sessions.save(new PlanningSession(UUID.randomUUID(), workItemId, maxRounds)));
        item.moveTo(WorkItemStatus.PLANNING);
        workItems.save(item);
        return analyze(item, session);
    }

    @Transactional
    public PlanningView continuePlanning(UUID workItemId) {
        WorkItem item = requiredWorkItem(workItemId);
        PlanningSession session = requiredSession(workItemId);
        if (session.getStatus() != PlanningStatus.WAITING_FOR_USER_INPUT) {
            throw new IllegalStateException("Planning is not waiting for user input");
        }
        ensureBlockingQuestionsAnswered(session);
        if (!session.canStartAnotherRound()) {
            session.requireHuman("Maximum planning rounds reached; human planning is required.", session.getLastAnalysisJson());
            item.moveTo(WorkItemStatus.PLANNING_HUMAN_REQUIRED);
            sessions.save(session);
            workItems.save(item);
            return view(session);
        }
        item.moveTo(WorkItemStatus.PLANNING);
        workItems.save(item);
        return analyze(item, session);
    }

    @Transactional
    public PlanningQuestion answer(UUID workItemId, UUID questionId, String answer, String answeredBy) {
        PlanningSession session = requiredSession(workItemId);
        if (session.getStatus() != PlanningStatus.WAITING_FOR_USER_INPUT) {
            throw new IllegalStateException("Planning is not waiting for user input");
        }
        PlanningQuestion question = questions.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Planning question not found"));
        if (!question.getSessionId().equals(session.getId())) throw new IllegalArgumentException("Question does not belong to this planning session");
        question.answer(answer, answeredBy);
        return questions.save(question);
    }

    @Transactional
    public PlanningView approve(UUID workItemId) {
        WorkItem item = requiredWorkItem(workItemId);
        PlanningSession session = requiredSession(workItemId);
        if (session.getStatus() != PlanningStatus.READY_FOR_REVIEW || item.getStatus() != WorkItemStatus.READY_FOR_PLANNING_REVIEW) {
            throw new IllegalStateException("Planning is not ready for approval");
        }
        session.approve();
        item.setSpecification(session.getFinalSpecification());
        item.moveTo(WorkItemStatus.READY_FOR_DEVELOPMENT);
        sessions.save(session);
        workItems.save(item);
        return view(session);
    }

    @Transactional(readOnly = true)
    public PlanningView get(UUID workItemId) {
        return view(requiredSession(workItemId));
    }

    @Transactional(readOnly = true)
    public List<PlanningQuestion> listQuestions(UUID workItemId) {
        PlanningSession session = requiredSession(workItemId);
        return questions.findBySessionIdOrderByRoundAscCreatedAtAsc(session.getId());
    }

    private PlanningView analyze(WorkItem item, PlanningSession session) {
        session.startRound();
        sessions.save(session);

        AgentExecution execution = new AgentExecution(UUID.randomUUID(), item.getId(), AgentType.REFINER,
                "Interactive planning round " + session.getRound() + ": " + item.getTitle());
        executions.save(execution);

        try {
            ProductPlanningAgent.Result result = agent.analyze(
                    item.getTitle(), item.getDescription(), session.getRound(), buildConversation(session));
            PlanningAnalysis analysis = result.analysis();
            persistQuestions(session, analysis);

            switch (analysis.status()) {
                case NEEDS_INPUT -> {
                    session.waitForInput(analysis.summary(), result.rawJson());
                    item.moveTo(WorkItemStatus.WAITING_FOR_USER_INPUT);
                }
                case READY_FOR_REVIEW -> {
                    session.readyForReview(analysis.summary(), result.rawJson(), analysis.specificationMarkdown());
                    item.moveTo(WorkItemStatus.READY_FOR_PLANNING_REVIEW);
                }
                case HUMAN_REQUIRED -> {
                    session.requireHuman(analysis.summary(), result.rawJson());
                    item.moveTo(WorkItemStatus.PLANNING_HUMAN_REQUIRED);
                }
            }

            sessions.save(session);
            workItems.save(item);
            execution.succeed(result.rawJson());
            executions.save(execution);
            return view(session);
        } catch (RuntimeException e) {
            session.fail(e.getMessage());
            item.moveTo(WorkItemStatus.FAILED);
            sessions.save(session);
            workItems.save(item);
            execution.fail(e.getMessage());
            executions.save(execution);
            throw e;
        }
    }

    private void persistQuestions(PlanningSession session, PlanningAnalysis analysis) {
        for (PlanningAnalysis.QuestionDraft draft : analysis.questions()) {
            if (draft.question() == null || draft.question().isBlank()) continue;
            try {
                questions.save(new PlanningQuestion(
                        UUID.randomUUID(),
                        session.getId(),
                        session.getRound(),
                        draft.category() == null ? PlanningQuestionCategory.OTHER : draft.category(),
                        draft.question().trim(),
                        draft.rationale() == null ? "Business clarification required." : draft.rationale().trim(),
                        draft.blocking(),
                        mapper.writeValueAsString(draft.options())));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to persist planning question", e);
            }
        }
    }

    private String buildConversation(PlanningSession session) {
        StringBuilder out = new StringBuilder();
        for (PlanningQuestion q : questions.findBySessionIdOrderByRoundAscCreatedAtAsc(session.getId())) {
            out.append("ROUND ").append(q.getRound()).append("\nQUESTION [")
                    .append(q.getCategory()).append("]: ").append(q.getQuestion()).append('\n')
                    .append("RATIONALE: ").append(q.getRationale()).append('\n')
                    .append("ANSWER: ").append(q.answered() ? q.getAnswer() : "<UNANSWERED>").append("\n\n");
        }
        return out.toString();
    }

    private void ensureBlockingQuestionsAnswered(PlanningSession session) {
        List<PlanningQuestion> current = questions.findBySessionIdAndRoundOrderByCreatedAtAsc(session.getId(), session.getRound());
        List<UUID> unanswered = current.stream()
                .filter(PlanningQuestion::isBlocking)
                .filter(q -> !q.answered())
                .map(PlanningQuestion::getId)
                .toList();
        if (!unanswered.isEmpty()) throw new IllegalStateException("Blocking planning questions still unanswered: " + unanswered);
    }

    private WorkItem requiredWorkItem(UUID id) {
        return workItems.findById(id).orElseThrow(() -> new NoSuchElementException("WorkItem not found"));
    }

    private PlanningSession requiredSession(UUID workItemId) {
        return sessions.findByWorkItemId(workItemId).orElseThrow(() -> new NoSuchElementException("Planning session not found"));
    }

    private PlanningView view(PlanningSession session) {
        List<PlanningQuestion> qs = questions.findBySessionIdOrderByRoundAscCreatedAtAsc(session.getId());
        long unansweredBlocking = qs.stream().filter(PlanningQuestion::isBlocking).filter(q -> !q.answered()).count();
        return new PlanningView(session, qs, unansweredBlocking);
    }

    public record PlanningView(PlanningSession session, List<PlanningQuestion> questions, long unansweredBlockingQuestions) {}
}
