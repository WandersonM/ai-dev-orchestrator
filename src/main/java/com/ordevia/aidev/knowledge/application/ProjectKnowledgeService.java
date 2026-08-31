package com.ordevia.aidev.knowledge.application;

import com.ordevia.aidev.audit.application.AuditService;
import com.ordevia.aidev.knowledge.domain.*;
import com.ordevia.aidev.knowledge.infrastructure.ProjectKnowledgeJpaRepository;
import com.ordevia.aidev.project.infrastructure.ProjectJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProjectKnowledgeService {
    private final ProjectKnowledgeJpaRepository knowledge;
    private final ProjectJpaRepository projects;
    private final AuditService audit;

    public ProjectKnowledgeService(ProjectKnowledgeJpaRepository knowledge, ProjectJpaRepository projects, AuditService audit) {
        this.knowledge=knowledge; this.projects=projects; this.audit=audit;
    }

    @Transactional
    public ProjectKnowledge add(UUID projectId, KnowledgeType type, String statement, String sourceType,
                                String sourceRef, KnowledgeConfidence confidence, String createdBy) {
        if(!projects.existsById(projectId)) throw new NoSuchElementException("Project not found");
        if(statement==null||statement.isBlank()) throw new IllegalArgumentException("Knowledge statement cannot be blank");
        if(sourceType==null||sourceType.isBlank()) throw new IllegalArgumentException("sourceType is required");
        ProjectKnowledge entry=knowledge.save(new ProjectKnowledge(UUID.randomUUID(),projectId,
                Objects.requireNonNull(type),statement.trim(),sourceType.trim(),sourceRef,
                confidence==null?KnowledgeConfidence.CONFIRMED:confidence,createdBy));
        audit.append(null,null,"PROJECT_KNOWLEDGE_ADDED","HUMAN",createdBy,"ProjectKnowledge",entry.getId().toString(),Map.of(
                "projectId",projectId.toString(),"type",type.name(),"sourceType",sourceType,"confidence",entry.getConfidence().name()));
        return entry;
    }

    @Transactional
    public ProjectKnowledge supersede(UUID projectId,UUID id,String actor){
        ProjectKnowledge entry=knowledge.findById(id).orElseThrow(()->new NoSuchElementException("Knowledge entry not found"));
        if(!projectId.equals(entry.getProjectId()))throw new IllegalArgumentException("Knowledge entry does not belong to project");
        entry.supersede();knowledge.save(entry);
        audit.append(null,null,"PROJECT_KNOWLEDGE_SUPERSEDED","HUMAN",actor,"ProjectKnowledge",id.toString(),Map.of("projectId",projectId.toString()));
        return entry;
    }

    @Transactional(readOnly=true)
    public List<ProjectKnowledge> active(UUID projectId){
        if(!projects.existsById(projectId))throw new NoSuchElementException("Project not found");
        return knowledge.findByProjectIdAndActiveTrueOrderByCreatedAtAsc(projectId);
    }

    @Transactional(readOnly=true)
    public String context(UUID projectId,String query){
        String normalized=Objects.toString(query,"").trim().toLowerCase(Locale.ROOT);
        List<ProjectKnowledge> entries=knowledge.findByProjectIdAndActiveTrueOrderByCreatedAtAsc(projectId);
        if(!normalized.isBlank()){
            List<String> tokens=Arrays.stream(normalized.split("\\s+")).filter(t->t.length()>=3).toList();
            entries=entries.stream().filter(e->{String hay=(e.getKnowledgeType()+" "+e.getStatement()+" "+Objects.toString(e.getSourceRef(),"")).toLowerCase(Locale.ROOT);return tokens.isEmpty()||tokens.stream().anyMatch(hay::contains);}).toList();
        }
        StringBuilder out=new StringBuilder("CONFIRMED PROJECT KNOWLEDGE (with provenance)\n");
        entries.stream().limit(80).forEach(e->out.append("- [").append(e.getKnowledgeType()).append("][").append(e.getConfidence()).append("] ")
                .append(e.getStatement()).append(" | source=").append(e.getSourceType()).append(":").append(Objects.toString(e.getSourceRef(),"n/a")).append('\n'));
        if(entries.isEmpty())out.append("No matching confirmed project knowledge. Do not invent missing business rules.\n");
        return out.toString();
    }
}
