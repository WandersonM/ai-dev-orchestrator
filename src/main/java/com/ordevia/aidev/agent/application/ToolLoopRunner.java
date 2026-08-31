package com.ordevia.aidev.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.agent.policy.AgentToolAccessService;
import com.ordevia.aidev.agent.tool.*;
import com.ordevia.aidev.execution.domain.ToolExecution;
import com.ordevia.aidev.execution.infrastructure.ToolExecutionJpaRepository;
import com.ordevia.aidev.llm.domain.*;
import com.ordevia.aidev.session.application.AgentSessionService;
import com.ordevia.aidev.session.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Component
public class ToolLoopRunner {
    private final LlmGateway llm;
    private final AgentToolAccessService toolAccess;
    private final ObjectMapper mapper;
    private final ToolExecutionJpaRepository toolExecutions;
    private final AgentSessionService sessions;

    public ToolLoopRunner(LlmGateway llm, AgentToolAccessService toolAccess, ObjectMapper mapper,
                          ToolExecutionJpaRepository toolExecutions, AgentSessionService sessions) {
        this.llm=llm; this.toolAccess=toolAccess; this.mapper=mapper; this.toolExecutions=toolExecutions; this.sessions=sessions;
    }

    public AgentResult run(AgentType agentType, LlmTask task, AgentContext context, int maxSteps, String systemPrompt, String userPrompt) {
        var session = sessions.openOrResume(context.workItemId(), agentType);
        int step = session.getCurrentStep();
        try {
            List<ToolExecution> previous=toolExecutions.findByWorkItemIdAndAgentTypeOrderByStepNumberAsc(context.workItemId(),agentType);
            String transcript=buildTranscript(previous);
            step=Math.max(step,previous.stream().mapToInt(ToolExecution::getStepNumber).max().orElse(0));
            String workspaceManifest=Objects.toString(context.metadata().getOrDefault("workspaceManifest","Single repository workspace."),"");
            String basePrompt=userPrompt+"\n\nWORKSPACE MANIFEST:\n"+workspaceManifest+
                    "\n\nIMPORTANT MULTI-ROOT RULES:\nWhen multiple roots exist, prefix file paths with repository alias and set run_command.cwd to that alias. " +
                    "Do not assume roots share runtime, conventions or build tools.\n\nPREVIOUS PERSISTED TOOL HISTORY:\n"+transcript;
            LlmToolRequest request=LlmToolRequest.initial(task,systemPrompt,basePrompt,toolDefinitions(agentType));

            while(step<maxSteps){
                var control=sessions.controlPoint(session.getId(),step,AgentCheckpointType.BEFORE_LLM,"Before LLM turn",request.previousTurnId());
                if(control.status()==AgentSessionStatus.PAUSED){
                    sessions.awaitResumeOrCancel(session.getId());
                    control=sessions.controlPoint(session.getId(),step,AgentCheckpointType.RESUMED,"Resumed before LLM turn",null);
                }
                if(control.status()==AgentSessionStatus.CANCELLED) throw new AgentSessionService.AgentSessionCancelledException("Agent session cancelled");
                if(!control.humanMessages().isEmpty()){
                    transcript=buildTranscript(toolExecutions.findByWorkItemIdAndAgentTypeOrderByStepNumberAsc(context.workItemId(),agentType));
                    request=LlmToolRequest.initial(task,systemPrompt,basePrompt+"\n\nLATEST TOOL HISTORY:\n"+transcript+
                            "\n\nLIVE HUMAN GUIDANCE:\n- "+String.join("\n- ",control.humanMessages()),toolDefinitions(agentType));
                }

                LlmToolResponse response=llm.executeTools(request);
                sessions.controlPoint(session.getId(),step,AgentCheckpointType.AFTER_LLM,trim(response.text(),4000),response.turnId());
                if(!response.hasToolCalls()){
                    if(StringUtils.hasText(response.text())){ sessions.complete(session.getId(),step,trim(response.text(),8000)); return AgentResult.success(response.text()); }
                    return fail(session.getId(),step,agentType+" finished without tool calls or a final report");
                }
                if(!StringUtils.hasText(response.turnId())) return fail(session.getId(),step,"LLM provider did not return a continuation turn id");

                List<LlmToolResult> results=new ArrayList<>();
                for(LlmToolCall call:response.toolCalls()){
                    if(step>=maxSteps) break;
                    step++;
                    ToolExecution execution=new ToolExecution(UUID.randomUUID(),context.workItemId(),agentType,step,call.name(),mapper.writeValueAsString(call.arguments()));
                    toolExecutions.saveAndFlush(execution);
                    String output;
                    try{
                        ToolResult result=toolAccess.required(agentType,call.name()).execute(context,call.arguments());
                        if(result.success()){execution.succeed(result.output());output=result.output();}else{execution.fail(result.error());output="ERROR: "+result.error();}
                    }catch(Exception e){execution.fail(e.getMessage());output="ERROR: "+e.getMessage();}
                    toolExecutions.save(execution);
                    var afterTool=sessions.controlPoint(session.getId(),step,AgentCheckpointType.AFTER_TOOL,call.name()+" => "+trim(output,4000),response.turnId());
                    if(afterTool.status()==AgentSessionStatus.PAUSED){sessions.awaitResumeOrCancel(session.getId());}
                    if(afterTool.status()==AgentSessionStatus.CANCELLED) throw new AgentSessionService.AgentSessionCancelledException("Agent session cancelled");
                    results.add(new LlmToolResult(call.id(),call.name(),output));
                }
                request=request.continueWith(response.turnId(),results);
            }
            return fail(session.getId(),step,agentType+" exceeded max steps: "+maxSteps);
        }catch(AgentSessionService.AgentSessionCancelledException e){ return AgentResult.failure("SESSION_CANCELLED: "+e.getMessage()); }
        catch(Exception e){ sessions.fail(session.getId(),step,e.getMessage()); return AgentResult.failure(e.getMessage()); }
    }

    private AgentResult fail(UUID sessionId,int step,String error){sessions.fail(sessionId,step,error);return AgentResult.failure(error);}

    private List<LlmToolDefinition> toolDefinitions(AgentType agentType){
        List<LlmToolDefinition> defs=new ArrayList<>();
        for(AgentTool tool:toolAccess.allowedTools(agentType)){
            Map<String,Object> schema=switch(tool.name()){
                case "search_code"->objectSchema(Map.of("query",Map.of("type","string")),List.of("query"));
                case "read_file"->objectSchema(Map.of("path",Map.of("type","string")),List.of("path"));
                case "write_file"->objectSchema(Map.of("path",Map.of("type","string"),"content",Map.of("type","string")),List.of("path","content"));
                case "run_command"->objectSchema(Map.of("command",Map.of("type","array","items",Map.of("type","string")),"cwd",Map.of("type","string")),List.of("command"));
                default->tool.inputSchema();
            };
            defs.add(new LlmToolDefinition(tool.name(),tool.description(),schema));
        }
        return defs;
    }
    private Map<String,Object> objectSchema(Map<String,Object> properties,List<String> required){Map<String,Object>s=new LinkedHashMap<>();s.put("type","object");s.put("properties",properties);s.put("required",required);s.put("additionalProperties",false);return s;}
    private String buildTranscript(List<ToolExecution> executions){String transcript="";for(ToolExecution e:executions){String outcome=switch(e.getStatus()){case SUCCEEDED->e.getOutputText();case FAILED->"ERROR: "+e.getErrorMessage();case RUNNING->"INTERRUPTED";};transcript=append(transcript,"STEP "+e.getStepNumber()+" TOOL "+e.getToolName()+" ARGS "+e.getArgumentsJson()+" => "+outcome);}return transcript;}
    private String append(String t,String line){String u=t+"\n"+line;return u.length()>50_000?u.substring(u.length()-50_000):u;}
    private String trim(String v,int max){if(v==null)return null;return v.length()<=max?v:v.substring(0,max);}
}
