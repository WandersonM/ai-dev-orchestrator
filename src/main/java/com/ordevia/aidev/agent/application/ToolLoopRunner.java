package com.ordevia.aidev.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.domain.AgentContext;
import com.ordevia.aidev.agent.domain.AgentResult;
import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.agent.policy.AgentToolAccessService;
import com.ordevia.aidev.agent.tool.AgentTool;
import com.ordevia.aidev.agent.tool.ToolResult;
import com.ordevia.aidev.execution.domain.ToolExecution;
import com.ordevia.aidev.execution.infrastructure.ToolExecutionJpaRepository;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Component
public class ToolLoopRunner {
    private final LlmGateway llm;
    private final AgentToolAccessService toolAccess;
    private final ObjectMapper mapper;
    private final ToolExecutionJpaRepository toolExecutions;

    public ToolLoopRunner(LlmGateway llm,
                          AgentToolAccessService toolAccess,
                          ObjectMapper mapper,
                          ToolExecutionJpaRepository toolExecutions) {
        this.llm = llm;
        this.toolAccess = toolAccess;
        this.mapper = mapper;
        this.toolExecutions = toolExecutions;
    }

    public AgentResult run(AgentType agentType,
                           LlmTask task,
                           AgentContext context,
                           int maxSteps,
                           String systemPrompt,
                           String userPrompt) {
        try {
            List<ToolExecution> previous = toolExecutions.findByWorkItemIdAndAgentTypeOrderByStepNumberAsc(context.workItemId(), agentType);
            String transcript = buildTranscript(previous);
            int step = previous.stream().mapToInt(ToolExecution::getStepNumber).max().orElse(0);
            String prompt = userPrompt + "\n\nPREVIOUS PERSISTED TOOL HISTORY:\n" + transcript;
            LlmToolRequest request = LlmToolRequest.initial(task, systemPrompt, prompt, toolDefinitions(agentType));

            while (step < maxSteps) {
                LlmToolResponse response = llm.executeTools(request);
                if (!response.hasToolCalls()) {
                    if (StringUtils.hasText(response.text())) return AgentResult.success(response.text());
                    return AgentResult.failure(agentType + " finished without tool calls or a final report");
                }
                if (!StringUtils.hasText(response.turnId())) return AgentResult.failure("LLM provider did not return a continuation turn id");

                List<LlmToolResult> results = new ArrayList<>();
                for (LlmToolCall call : response.toolCalls()) {
                    if (step >= maxSteps) return AgentResult.failure(agentType + " exceeded max steps: " + maxSteps);
                    step++;
                    ToolExecution execution = new ToolExecution(
                            UUID.randomUUID(), context.workItemId(), agentType, step, call.name(), mapper.writeValueAsString(call.arguments()));
                    toolExecutions.saveAndFlush(execution);
                    String providerOutput;
                    try {
                        ToolResult result = toolAccess.required(agentType, call.name()).execute(context.repository(), call.arguments());
                        if (result.success()) {
                            execution.succeed(result.output());
                            providerOutput = result.output();
                        } else {
                            execution.fail(result.error());
                            providerOutput = "ERROR: " + result.error();
                        }
                    } catch (Exception e) {
                        execution.fail(e.getMessage());
                        providerOutput = "ERROR: " + e.getMessage();
                    }
                    toolExecutions.save(execution);
                    results.add(new LlmToolResult(call.id(), call.name(), providerOutput));
                }
                request = request.continueWith(response.turnId(), results);
            }
            return AgentResult.failure(agentType + " exceeded max steps: " + maxSteps);
        } catch (Exception e) {
            return AgentResult.failure(e.getMessage());
        }
    }

    private List<LlmToolDefinition> toolDefinitions(AgentType agentType) {
        List<LlmToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : toolAccess.allowedTools(agentType)) {
            Map<String, Object> schema = switch (tool.name()) {
                case "search_code" -> objectSchema(
                        Map.of("query", Map.of("type", "string", "description", "Text, class, method or symbol to search for")),
                        List.of("query"));
                case "read_file" -> objectSchema(
                        Map.of("path", Map.of("type", "string", "description", "Repository-relative file path")),
                        List.of("path"));
                case "write_file" -> objectSchema(
                        Map.of("path", Map.of("type", "string"), "content", Map.of("type", "string")),
                        List.of("path", "content"));
                case "run_command" -> objectSchema(
                        Map.of("command", Map.of("type", "array", "items", Map.of("type", "string"))),
                        List.of("command"));
                default -> tool.inputSchema();
            };
            definitions.add(new LlmToolDefinition(tool.name(), tool.description(), schema));
        }
        return definitions;
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private String buildTranscript(List<ToolExecution> executions) {
        String transcript = "";
        for (ToolExecution execution : executions) {
            String outcome = switch (execution.getStatus()) {
                case SUCCEEDED -> execution.getOutputText();
                case FAILED -> "ERROR: " + execution.getErrorMessage();
                case RUNNING -> "INTERRUPTED: previous process stopped before recording a result";
            };
            transcript = appendTranscript(transcript,
                    "STEP " + execution.getStepNumber() + " TOOL " + execution.getToolName() +
                            " ARGS " + execution.getArgumentsJson() + " => " + outcome);
        }
        return transcript;
    }

    private String appendTranscript(String transcript, String line) {
        String updated = transcript + "\n" + line;
        return updated.length() > 50_000 ? updated.substring(updated.length() - 50_000) : updated;
    }
}
