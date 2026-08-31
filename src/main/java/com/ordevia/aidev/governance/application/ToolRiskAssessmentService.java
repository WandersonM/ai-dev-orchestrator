package com.ordevia.aidev.governance.application;

import com.ordevia.aidev.governance.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ToolRiskAssessmentService {
    private final AutonomyLevel autonomy;

    public ToolRiskAssessmentService(@Value("${aidev.governance.autonomy:STANDARD}") AutonomyLevel autonomy) {
        this.autonomy = autonomy;
    }

    public RiskAssessment assess(String toolName, Map<String,Object> arguments) {
        String name = toolName.toLowerCase(Locale.ROOT);
        Set<ToolCapability> capabilities = new LinkedHashSet<>();
        RiskLevel risk;

        if (name.equals("read_file") || name.equals("search_code") || name.equals("project_knowledge")) {
            capabilities.add(ToolCapability.READ);
            risk = RiskLevel.LOW;
        } else if (name.equals("write_file")) {
            capabilities.add(ToolCapability.WRITE);
            risk = RiskLevel.MEDIUM;
        } else if (name.equals("run_command")) {
            return assessCommand(arguments);
        } else if (name.startsWith("mcp_")) {
            risk = assessMcp(name, capabilities);
        } else {
            capabilities.add(ToolCapability.EXECUTE);
            risk = RiskLevel.MEDIUM;
        }

        boolean allowed = allowedByAutonomy(capabilities);
        boolean approval = allowed && requiresApproval(risk);
        return new RiskAssessment(risk, Set.copyOf(capabilities), allowed, approval, reason(risk, capabilities, approval));
    }

    private RiskAssessment assessCommand(Map<String,Object> arguments) {
        List<String> command = toStrings(arguments.get("command"));
        Set<ToolCapability> caps = new LinkedHashSet<>();
        caps.add(ToolCapability.EXECUTE);
        RiskLevel risk = RiskLevel.MEDIUM;
        if (!command.isEmpty() && command.getFirst().equals("git")) {
            caps.add(ToolCapability.GIT_READ);
            String operation = command.size() > 1 ? command.get(1).toLowerCase(Locale.ROOT) : "";
            if (Set.of("status","diff","log","show","branch","rev-parse").contains(operation)) {
                risk = RiskLevel.LOW;
            } else if (Set.of("add","commit","checkout","switch","merge","rebase","reset","stash").contains(operation)) {
                caps.add(ToolCapability.GIT_WRITE);
                risk = RiskLevel.HIGH;
            } else if (Set.of("fetch","pull").contains(operation)) {
                caps.add(ToolCapability.GIT_WRITE); caps.add(ToolCapability.NETWORK);
                risk = RiskLevel.HIGH;
            } else if (Set.of("push","remote").contains(operation)) {
                caps.add(ToolCapability.GIT_WRITE); caps.add(ToolCapability.NETWORK); caps.add(ToolCapability.EXTERNAL_MUTATION);
                risk = RiskLevel.CRITICAL;
            }
        }
        boolean allowed = allowedByAutonomy(caps);
        boolean approval = allowed && requiresApproval(risk);
        return new RiskAssessment(risk, Set.copyOf(caps), allowed, approval, reason(risk,caps,approval));
    }

    private RiskLevel assessMcp(String name, Set<ToolCapability> caps) {
        boolean database = name.contains("postgres") || name.contains("mysql") || name.contains("database") || name.contains("sql");
        boolean mutation = containsAny(name,"create","update","delete","drop","write","execute","send","publish","move","merge","deploy","cancel");
        boolean production = name.contains("prod") || name.contains("production") || name.contains("deploy");
        caps.add(ToolCapability.NETWORK);
        if (database) caps.add(mutation ? ToolCapability.DATABASE_WRITE : ToolCapability.DATABASE_READ);
        else caps.add(mutation ? ToolCapability.EXTERNAL_MUTATION : ToolCapability.READ);
        if (production) caps.add(ToolCapability.PRODUCTION);
        if (production) return RiskLevel.CRITICAL;
        if (mutation && database) return RiskLevel.CRITICAL;
        if (mutation) return RiskLevel.HIGH;
        return RiskLevel.MEDIUM;
    }

    private boolean allowedByAutonomy(Set<ToolCapability> caps) {
        if (autonomy != AutonomyLevel.READ_ONLY) return true;
        return caps.stream().allMatch(c -> c == ToolCapability.READ || c == ToolCapability.GIT_READ || c == ToolCapability.DATABASE_READ);
    }

    private boolean requiresApproval(RiskLevel risk) {
        return switch (autonomy) {
            case READ_ONLY -> false;
            case ASSISTED -> risk.ordinal() >= RiskLevel.MEDIUM.ordinal();
            case STANDARD -> risk.ordinal() >= RiskLevel.HIGH.ordinal();
            case AGGRESSIVE -> risk == RiskLevel.CRITICAL;
        };
    }

    private String reason(RiskLevel risk, Set<ToolCapability> caps, boolean approval) {
        return "risk="+risk+", capabilities="+caps+", autonomy="+autonomy+(approval?", human approval required":"");
    }

    private boolean containsAny(String value,String... terms){for(String term:terms)if(value.contains(term))return true;return false;}
    private List<String> toStrings(Object raw){if(!(raw instanceof List<?> list))return List.of();return list.stream().map(String::valueOf).toList();}

    public record RiskAssessment(RiskLevel riskLevel, Set<ToolCapability> capabilities, boolean allowed, boolean approvalRequired, String reason) {}
}
