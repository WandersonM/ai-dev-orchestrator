package com.ordevia.aidev.agent.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.agent.tool.*;
import com.ordevia.aidev.execution.domain.ToolExecution;
import com.ordevia.aidev.execution.infrastructure.ToolExecutionJpaRepository;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BackendDeveloperAgent implements Agent {
    private final LlmGateway llm;
    private final ToolRegistry tools;
    private final ObjectMapper mapper;
    private final ToolExecutionJpaRepository toolExecutions;
    private final int maxSteps;

    public BackendDeveloperAgent(LlmGateway llm,
                                 ToolRegistry tools,
                                 ObjectMapper mapper,
                                 ToolExecutionJpaRepository toolExecutions,
                                 @Value("${aidev.agents.backend.max-steps:20}") int maxSteps) {
        this.llm = llm;
        this.tools = tools;
        this.mapper = mapper;
        this.toolExecutions = toolExecutions;
        this.maxSteps = maxSteps;
    }

    @Override
    public AgentType type() {
        return AgentType.BACKEND_DEVELOPER;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        try {
            List<ToolExecution> previous = toolExecutions.findByWorkItemIdAndAgentTypeOrderByStepNumberAsc(context.workItemId(), type());
            String transcript = buildTranscript(previous);
            int firstStep = previous.stream().mapToInt(ToolExecution::getStepNumber).max().orElse(0) + 1;

            for (int step = firstStep; step <= maxSteps; step++) {
                String response = llm.execute(new LlmRequest(
                        LlmTask.BACKEND_IMPLEMENTATION,
                        systemPrompt(),
                        userPrompt(context, transcript, step))).content();

                Map<String, Object> action = parseJson(response);
                String actionType = String.valueOf(action.get("type"));

                if ("complete".equals(actionType)) {
                    return AgentResult.success(String.valueOf(action.getOrDefault("report", response)));
                }

                if (!"tool".equals(actionType)) {
                    transcript = appendTranscript(transcript, "STEP " + step + " INVALID_RESPONSE => " + response);
                    continue;
                }

                String toolName = String.valueOf(action.get("tool"));
                @SuppressWarnings("unchecked")
                Map<String, Object> args = action.get("arguments") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : Map.of();

                ToolExecution execution = new ToolExecution(
                        UUID.randomUUID(),
                        context.workItemId(),
                        type(),
                        step,
                        toolName,
                        mapper.writeValueAsString(args));
                toolExecutions.saveAndFlush(execution);

                ToolResult result;
                try {
                    result = tools.required(toolName).execute(context.repository(), args);
                    if (result.success()) {
                        execution.succeed(result.output());
                    } else {
                        execution.fail(result.error());
                    }
                } catch (Exception e) {
                    execution.fail(e.getMessage());
                    toolExecutions.save(execution);
                    transcript = appendTranscript(transcript, "STEP " + step + " TOOL " + toolName + " => ERROR: " + e.getMessage());
                    continue;
                }

                toolExecutions.save(execution);
                transcript = appendTranscript(
                        transcript,
                        "STEP " + step + " TOOL " + toolName + " ARGS " + execution.getArgumentsJson() + " => " +
                                (result.success() ? result.output() : "ERROR: " + result.error()));
            }

            return AgentResult.failure("Backend agent exceeded max steps: " + maxSteps);
        } catch (Exception e) {
            return AgentResult.failure(e.getMessage());
        }
    }

    private String buildTranscript(List<ToolExecution> executions) {
        String transcript = "";
        for (ToolExecution execution : executions) {
            String outcome = switch (execution.getStatus()) {
                case SUCCEEDED -> execution.getOutputText();
                case FAILED -> "ERROR: " + execution.getErrorMessage();
                case RUNNING -> "INTERRUPTED: previous process stopped before recording a result";
            };
            transcript = appendTranscript(
                    transcript,
                    "STEP " + execution.getStepNumber() + " TOOL " + execution.getToolName() +
                            " ARGS " + execution.getArgumentsJson() + " => " + outcome);
        }
        return transcript;
    }

    private String appendTranscript(String transcript, String line) {
        String updated = transcript + "\n" + line;
        return updated.length() > 50_000 ? updated.substring(updated.length() - 50_000) : updated;
    }

    private Map<String, Object> parseJson(String raw) throws Exception {
        String json = raw.trim();
        if (json.startsWith("```")) {
            int firstNl = json.indexOf('\n');
            int last = json.lastIndexOf("```");
            if (firstNl >= 0 && last > firstNl) {
                json = json.substring(firstNl + 1, last).trim();
            }
        }
        return mapper.readValue(json, new TypeReference<>() {});
    }

    private String systemPrompt() {
        return """
                You are a Staff Backend Engineer operating an existing repository through tools.
                Never invent successful tool results. Inspect the code before editing it. Preserve architecture and conventions.
                Prefer search_code before broad reads. Run tests or compilation before completing when the repository supports them.
                Previous tool results may come from an earlier process execution; treat them as authoritative history.
                Respond ONLY with one JSON object per turn.
                To use a tool: {"type":"tool","tool":"search_code|read_file|write_file|run_command","arguments":{...}}
                search_code arguments: {"query":"text"}
                read_file arguments: {"path":"relative/path"}
                write_file arguments: {"path":"relative/path","content":"full file contents"}
                run_command arguments: {"command":["git","status","--short"]}
                To finish: {"type":"complete","report":"markdown implementation report including files changed, tests, risks and remaining work"}
                Never include markdown fences around the JSON.
                """;
    }

    private String userPrompt(AgentContext c, String transcript, int step) {
        return "TITLE: " + c.title() +
                "\nDESCRIPTION: " + c.description() +
                "\nSPECIFICATION:\n" + c.specification() +
                "\nRepository: " + c.repository() +
                "\nStep: " + step +
                "\nPrevious tool results:\n" + transcript;
    }
}
