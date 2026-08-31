package com.ordevia.aidev.agent.tool;

import com.ordevia.aidev.agent.domain.AgentContext;
import com.ordevia.aidev.knowledge.application.ProjectKnowledgeService;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class ProjectKnowledgeTool implements AgentTool {
    private final WorkItemJpaRepository workItems;
    private final ProjectKnowledgeService knowledge;

    public ProjectKnowledgeTool(WorkItemJpaRepository workItems, ProjectKnowledgeService knowledge) {
        this.workItems=workItems; this.knowledge=knowledge;
    }

    @Override public String name(){return "project_knowledge";}
    @Override public String description(){return "Read confirmed project/domain knowledge with provenance. Use before assuming business rules, legacy behavior or cross-card decisions.";}

    @Override
    public Map<String,Object> inputSchema(){
        Map<String,Object> schema=new LinkedHashMap<>();
        schema.put("type","object");
        schema.put("properties",Map.of("query",Map.of("type","string","description","Optional keywords to filter confirmed project knowledge")));
        schema.put("additionalProperties",false);
        return schema;
    }

    @Override public ToolResult execute(Path workspace,Map<String,Object> arguments){return ToolResult.fail("project_knowledge requires WorkItem context");}

    @Override
    public ToolResult execute(AgentContext context,Map<String,Object> arguments){
        var item=workItems.findById(context.workItemId()).orElse(null);
        if(item==null||item.getProjectId()==null)return ToolResult.ok("No project-scoped knowledge is available for this WorkItem.");
        String query=Objects.toString(arguments.getOrDefault("query",""),"");
        return ToolResult.ok(knowledge.context(item.getProjectId(),query));
    }
}
