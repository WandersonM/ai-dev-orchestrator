package com.ordevia.aidev.planning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.llm.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductPlanningAgentTest {

    @Test
    void acceptsBlockingQuestionsWhenMoreBusinessInputIsNeeded() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.execute(any())).thenReturn(new LlmResponse("""
                {
                  "status":"NEEDS_INPUT",
                  "summary":"A regra de alteração ainda precisa ser confirmada.",
                  "questions":[{
                    "category":"BUSINESS_RULE",
                    "question":"Quais status permitem a alteração?",
                    "rationale":"Isso muda a validação do domínio.",
                    "blocking":true,
                    "options":["ABERTO","ABERTO e VENCIDO"]
                  }],
                  "facts":[],
                  "assumptions":[],
                  "decisions":[],
                  "specificationMarkdown":""
                }
                """, LlmProvider.GEMINI, "test"));

        ProductPlanningAgent agent = new ProductPlanningAgent(gateway, new ObjectMapper());
        ProductPlanningAgent.Result result = agent.analyze("Alterar vencimento", "Permitir alterar vencimento", 1, "");

        assertEquals(PlanningAnalysis.PlanningOutcome.NEEDS_INPUT, result.analysis().status());
        assertEquals(1, result.analysis().questions().size());
        assertTrue(result.analysis().questions().getFirst().blocking());
    }

    @Test
    void rejectsReadyPlanWhenItStillContainsBlockingAssumptions() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.execute(any())).thenReturn(new LlmResponse("""
                {
                  "status":"READY_FOR_REVIEW",
                  "summary":"Pronto.",
                  "questions":[],
                  "facts":[],
                  "assumptions":[{"statement":"Título pago pode ser alterado.","blocking":true,"reason":"Não confirmado."}],
                  "decisions":[],
                  "specificationMarkdown":"# Specification"
                }
                """, LlmProvider.GEMINI, "test"));

        ProductPlanningAgent agent = new ProductPlanningAgent(gateway, new ObjectMapper());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> agent.analyze("Alterar vencimento", "Descrição", 1, ""));
        assertTrue(ex.getMessage().contains("blocking assumptions"));
    }

    @Test
    void acceptsReadyPlanOnlyWithAConcreteSpecification() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.execute(any())).thenReturn(new LlmResponse("""
                {
                  "status":"READY_FOR_REVIEW",
                  "summary":"Requisitos confirmados.",
                  "questions":[],
                  "facts":[{"statement":"Somente títulos abertos podem mudar.","source":"USER_ANSWER"}],
                  "assumptions":[],
                  "decisions":[{"statement":"Guardar auditoria da alteração.","source":"USER_ANSWER"}],
                  "specificationMarkdown":"# Problem Statement\\nAlterar vencimento com auditoria."
                }
                """, LlmProvider.GEMINI, "test"));

        ProductPlanningAgent agent = new ProductPlanningAgent(gateway, new ObjectMapper());
        ProductPlanningAgent.Result result = agent.analyze("Alterar vencimento", "Descrição", 2, "respostas anteriores");

        assertEquals(PlanningAnalysis.PlanningOutcome.READY_FOR_REVIEW, result.analysis().status());
        assertTrue(result.analysis().specificationMarkdown().contains("Problem Statement"));
    }
}
