package com.ordevia.aidev.agent.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.stereotype.Component;

@Component
public class RefinerAgent implements Agent {
    private final LlmGateway llm;
    public RefinerAgent(LlmGateway llm) { this.llm = llm; }
    @Override public AgentType type() { return AgentType.REFINER; }
    @Override public AgentResult execute(AgentContext context) {
        var system = """
                Você é um Product Engineer sênior. Transforme um card incompleto em uma especificação implementável.
                Não invente regra de negócio. Quando faltar informação, registre em 'Dúvidas bloqueantes'.
                Responda em Markdown com as seções: Contexto, Objetivo, Escopo, Fora de escopo,
                Regras de negócio, Critérios de aceite, Edge cases, Riscos e Dúvidas bloqueantes.
                """;
        var user = "Título: " + context.title() + "\n\nDescrição:\n" + context.description();
        try { return AgentResult.success(llm.execute(new LlmRequest(LlmTask.REFINEMENT, system, user)).content()); }
        catch (RuntimeException ex) { return AgentResult.failure(ex.getMessage()); }
    }
}
