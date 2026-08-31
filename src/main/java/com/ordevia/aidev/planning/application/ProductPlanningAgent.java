package com.ordevia.aidev.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.llm.domain.LlmGateway;
import com.ordevia.aidev.llm.domain.LlmRequest;
import com.ordevia.aidev.llm.domain.LlmTask;
import org.springframework.stereotype.Component;

@Component
public class ProductPlanningAgent {
    private final LlmGateway llm;
    private final ObjectMapper mapper;

    public ProductPlanningAgent(LlmGateway llm, ObjectMapper mapper) {
        this.llm = llm;
        this.mapper = mapper;
    }

    public Result analyze(String title, String description, int round, String conversation) {
        String raw = llm.execute(new LlmRequest(LlmTask.REFINEMENT, systemPrompt(), userPrompt(title, description, round, conversation))).content();
        try {
            String json = stripCodeFence(raw);
            PlanningAnalysis analysis = mapper.readValue(json, PlanningAnalysis.class);
            validate(analysis);
            return new Result(analysis, json);
        } catch (Exception e) {
            throw new IllegalStateException("Planning agent returned an invalid structured response: " + e.getMessage(), e);
        }
    }

    private void validate(PlanningAnalysis analysis) {
        if (analysis.status() == null) throw new IllegalArgumentException("status is required");
        if (analysis.summary() == null || analysis.summary().isBlank()) throw new IllegalArgumentException("summary is required");

        if (analysis.status() == PlanningAnalysis.PlanningOutcome.NEEDS_INPUT &&
                analysis.questions().stream().noneMatch(PlanningAnalysis.QuestionDraft::blocking)) {
            throw new IllegalArgumentException("NEEDS_INPUT requires at least one blocking question");
        }

        if (analysis.status() == PlanningAnalysis.PlanningOutcome.READY_FOR_REVIEW) {
            if (analysis.specificationMarkdown() == null || analysis.specificationMarkdown().isBlank()) {
                throw new IllegalArgumentException("READY_FOR_REVIEW requires specificationMarkdown");
            }
            if (analysis.questions().stream().anyMatch(PlanningAnalysis.QuestionDraft::blocking)) {
                throw new IllegalArgumentException("READY_FOR_REVIEW cannot contain blocking questions");
            }
            if (analysis.hasBlockingAssumptions()) {
                throw new IllegalArgumentException("READY_FOR_REVIEW cannot contain blocking assumptions");
            }
        }
    }

    private String stripCodeFence(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty LLM response");
        String value = raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) value = value.substring(firstNewline + 1, lastFence).trim();
        }
        return value;
    }

    private String systemPrompt() {
        return """
                Você é um Principal Product/Planning Engineer responsável por descobrir e consolidar requisitos antes de qualquer implementação.

                Seu trabalho não é adivinhar. Se uma informação ausente puder alterar comportamento de negócio, dados, contrato/API,
                cobrança, integração externa, segurança, permissão, experiência do usuário, auditoria ou operação, faça uma pergunta.

                Não faça perguntas sobre detalhes técnicos que um engenheiro pode descobrir depois inspecionando o repositório.
                Faça no máximo 5 perguntas por rodada, priorizando as de maior impacto. Perguntas devem ser objetivas e explicar por que importam.
                Quando houver opções razoavelmente conhecidas, ofereça-as sem forçar escolha artificial.

                Classifique conhecimento explicitamente:
                - facts: informação presente no card ou já confirmada pelo humano;
                - decisions: decisão explícita tomada pelo humano;
                - assumptions: hipótese ainda não confirmada. Uma hipótese que muda comportamento relevante deve ser blocking=true.

                Só use READY_FOR_REVIEW quando não existir nenhuma pergunta bloqueante nem assumption bloqueante.
                A especificação final deve conter: Problem Statement, Business Context, Objetivo, Escopo, Fora de Escopo,
                Regras de Negócio, Critérios de Aceite verificáveis, Edge Cases, Dados, Integrações, Segurança/Permissões,
                Observabilidade/Auditoria, Cenários de Teste, Decisões Confirmadas, Assumptions não bloqueantes e Riscos.

                Retorne SOMENTE JSON válido, sem markdown fence e sem texto fora do JSON, no formato:
                {
                  "status": "NEEDS_INPUT|READY_FOR_REVIEW|HUMAN_REQUIRED",
                  "summary": "...",
                  "questions": [
                    {"category":"BUSINESS_RULE|SCOPE|USER_EXPERIENCE|DATA|INTEGRATION|SECURITY|OPERATIONS|OTHER",
                     "question":"...","rationale":"...","blocking":true,"options":["..."]}
                  ],
                  "facts": [{"statement":"...","source":"CARD|USER_ANSWER"}],
                  "assumptions": [{"statement":"...","blocking":false,"reason":"..."}],
                  "decisions": [{"statement":"...","source":"USER_ANSWER"}],
                  "specificationMarkdown":"..."
                }
                """;
    }

    private String userPrompt(String title, String description, int round, String conversation) {
        return "PLANNING ROUND: " + round +
                "\n\nTITLE:\n" + title +
                "\n\nORIGINAL DESCRIPTION:\n" + (description == null ? "" : description) +
                "\n\nPREVIOUS QUESTIONS AND HUMAN ANSWERS:\n" + (conversation == null || conversation.isBlank() ? "No previous interaction." : conversation);
    }

    public record Result(PlanningAnalysis analysis, String rawJson) {}
}
