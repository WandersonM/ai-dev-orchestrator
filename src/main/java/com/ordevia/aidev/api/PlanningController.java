package com.ordevia.aidev.api;

import com.ordevia.aidev.planning.application.PlanningService;
import com.ordevia.aidev.planning.domain.PlanningQuestion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/work-items/{workItemId}/planning")
public class PlanningController {
    private final PlanningService planning;

    public PlanningController(PlanningService planning) {
        this.planning = planning;
    }

    @PostMapping("/start")
    public PlanningService.PlanningView start(@PathVariable UUID workItemId) {
        return planning.start(workItemId);
    }

    @GetMapping
    public PlanningService.PlanningView get(@PathVariable UUID workItemId) {
        return planning.get(workItemId);
    }

    @GetMapping("/questions")
    public List<PlanningQuestion> questions(@PathVariable UUID workItemId) {
        return planning.listQuestions(workItemId);
    }

    @PostMapping("/questions/{questionId}/answer")
    public PlanningQuestion answer(@PathVariable UUID workItemId,
                                   @PathVariable UUID questionId,
                                   @Valid @RequestBody AnswerRequest request) {
        return planning.answer(workItemId, questionId, request.answer(), request.answeredBy());
    }

    @PostMapping("/continue")
    public PlanningService.PlanningView continuePlanning(@PathVariable UUID workItemId) {
        return planning.continuePlanning(workItemId);
    }

    @PostMapping("/approve")
    public PlanningService.PlanningView approve(@PathVariable UUID workItemId) {
        return planning.approve(workItemId);
    }

    public record AnswerRequest(@NotBlank String answer, String answeredBy) {}
}
