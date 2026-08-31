package com.ordevia.aidev.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.agent.tool.*;
import com.ordevia.aidev.execution.domain.ToolExecution;
import com.ordevia.aidev.execution.infrastructure.ToolExecutionJpaRepository;
import com.ordevia.aidev.llm.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Component
public class BackendDeveloperAgent implements Agent {
    private final LlmGateway llm;
    private final ToolRegistry tools;
    private final ObjectMapper mapper;
    private final ToolExecutionJpaRepository toolExecutions;
    private final int maxSteps;

    public BackendDeveloperAgent(LlmGateway llm, ToolRegistry tools, ObjectMapper mapper,
                                 ToolExecutionJpaRepository toolExecutions,
                                 @Value("${aidev.agents.backend.max-steps:20}") int maxSteps) {
        this.llm = llm;
        this.tools = tools;
        this.mapper = mapper;
        this.toolExecutions = toolExecutions;
        this.maxSteps = maxSteps;
    }

    @Override public AgentType type() { return AgentType.BACKEND_DEVELOPER; }

    @Override
    public AgentResult execute(AgentContext context) {
        try {
            List<ToolExecution> previous = toolExecutions.findByWorkItemIdAndAgentTypeOrderByStepNumberAsc(context.workItemId(), type());
            String transcript = buildTranscript(previous);
            int step = previous.stream().mapToInt(ToolExecution::getStepNumber).max().orElse(0);
            LlmToolRequest request = LlmToolRequest.initial(LlmTask.BACKEND_IMPLEMENTATION, systemPrompt(), userPrompt(context, transcript), toolDefinitions());

            while (step < maxSteps) {
                LlmToolResponse response = llm.executeTools(request);
                if (!response.hasToolCalls()) {
                    if (StringUtils.hasText(response.text())) return AgentResult.success(response.text());
                    return AgentResult.failure("LLM finished without tool calls or an implementation report");
                }
                if (!StringUtils.hasText(response.turnId())) return AgentResult.failure("LLM provider did not return a continuation turn id");

                List<LlmToolResult> results = new ArrayList<>();
                for (LlmToolCall call : response.toolCalls()) {
                    if (step >= maxSteps) return AgentResult.failure("Backend agent exceeded max steps: " + maxSteps);
                    step++;
                    ToolExecution execution = new ToolExecution(UUID.randomUUID(), context.workItemId(), type(), step, call.name(), mapper.writeValueAsString(call.arguments()));
                    toolExecutions.saveAndFlush(execution);
                    String providerOutput;
                    try {
                        ToolResult result = tools.required(call.name()).execute(context.repository(), call.arguments());
                        if (result.success()) { execution.succeed(result.output()); providerOutput = result.output(); }
                        else { execution.fail(result.error()); providerOutput = "ERROR: " + result.error(); }
                    } catch (Exception e) {
                        execution.fail(e.getMessage());
                        providerOutput = "ERROR: " + e.getMessage();
                    }
                    toolExecutions.save(execution);
                    results.add(new LlmToolResult(call.id(), call.name(), providerOutput));
                }
                request = request.continueWith(response.turnId(), results);
            }
            return AgentResult.failure("Backend agent exceeded max steps: " + maxSteps);
        } catch (Exception e) {
            return AgentResult.failure(e.getMessage());
        }
    }

    private List<LlmToolDefinition> toolDefinitions() {
        List<LlmToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : tools.all()) {
            Map<String, Object> schema = switch (tool.name()) {
                case "search_code" -> objectSchema(Map.of("query", Map.of("type", "string", "description", "Text, class, method or symbol to search for")), List.of("query"));
                case "read_file" -> objectSchema(Map.of("path", Map.of("type", "string", "description", "Repository-relative file path")), List.of("path"));
                case "write_file" -> objectSchema(Map.of("path", Map.of("type", "string"), "content", Map.of("type", "string")), List.of("path", "content"));
                case "run_command" -> objectSchema(Map.of("command", Map.of("type", "array", "items", Map.of("type", "string"))), List.of("command"));
                default -> tool.inputSchema();
            };
            definitions.add(new LlmToolDefinition(tool.name(), tool.description(), schema));
        }
        return definitions;
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object"); schema.put("properties", properties); schema.put("required", required); schema.put("additionalProperties", false);
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
            transcript = appendTranscript(transcript, "STEP " + execution.getStepNumber() + " TOOL " + execution.getToolName() + " ARGS " + execution.getArgumentsJson() + " => " + outcome);
        }
        return transcript;
    }

    private String appendTranscript(String transcript, String line) {
        String updated = transcript + "\n" + line;
        return updated.length() > 50_000 ? updated.substring(updated.length() - 50_000) : updated;
    }

    private String systemPrompt() {
        return """
                You are a Staff Backend Engineer operating an existing repository through provided tools.
                Inspect the repository before editing. Preserve the existing architecture, style and conventions.
                Prefer search_code before broad reads. Never invent tool results.
                MCP tools may provide external documentation, issue trackers, databases or other capabilities; use them only when relevant.
                Run tests or compilation before finishing when the repository supports them.
                When complete, return a concise markdown report containing files changed, tests, design decisions, risks and remaining work.
                """;
    }

    private String userPrompt(AgentContext c, String transcript) {
        return "TITLE: " + c.title() + "\nDESCRIPTION: " + c.description() + "\nSPECIFICATION:\n" + c.specification() +
                "\nRepository: " + c.repository() + "\nPrevious persisted tool history:\n" + transcript;
    }
}
