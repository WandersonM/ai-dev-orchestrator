package com.ordevia.aidev.github.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ordevia.aidev.github.domain.WorkItemPullRequest;
import com.ordevia.aidev.github.infrastructure.WorkItemPullRequestJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class GitHubReadinessService {
    private final WorkItemPullRequestJpaRepository pullRequests;
    private final RestClient github;
    private final String token;

    public GitHubReadinessService(WorkItemPullRequestJpaRepository pullRequests,RestClient.Builder builder,
                                  @Value("${aidev.github.token:}") String token,
                                  @Value("${aidev.github.api-base-url:https://api.github.com}") String apiBaseUrl){
        this.pullRequests=pullRequests;this.token=token;this.github=builder.baseUrl(apiBaseUrl).build();
    }

    public Readiness workItem(UUID workItemId){
        List<WorkItemPullRequest> prs=pullRequests.findByWorkItemIdOrderByRepositoryAliasAsc(workItemId);
        if(prs.isEmpty())return new Readiness(workItemId,false,false,List.of(),List.of("No coordinated pull requests exist"));
        List<RepositoryReadiness> repos=new ArrayList<>();List<String> blockers=new ArrayList<>();
        for(WorkItemPullRequest pr:prs){RepositoryReadiness rr=inspect(pr);repos.add(rr);for(String blocker:rr.blockers())blockers.add(pr.getRepositoryAlias()+": "+blocker);}
        boolean allMerged=repos.stream().allMatch(RepositoryReadiness::merged);
        boolean mergeReady=!allMerged&&blockers.isEmpty()&&repos.stream().allMatch(RepositoryReadiness::mergeReady);
        return new Readiness(workItemId,mergeReady,allMerged,List.copyOf(repos),List.copyOf(blockers));
    }

    private RepositoryReadiness inspect(WorkItemPullRequest stored){
        String[] slug=stored.getRepositorySlug().split("/",2);if(slug.length!=2)throw new IllegalStateException("Invalid GitHub repository slug: "+stored.getRepositorySlug());
        JsonNode pr=get("/repos/{owner}/{repo}/pulls/{number}",slug[0],slug[1],stored.getPullRequestNumber());
        boolean merged=pr.path("merged").asBoolean(false);boolean draft=pr.path("draft").asBoolean(false);String state=pr.path("state").asText();
        String headSha=pr.path("head").path("sha").asText();Boolean mergeable=pr.path("mergeable").isNull()?null:pr.path("mergeable").asBoolean();String mergeableState=pr.path("mergeable_state").asText("");
        List<Check> checks=checks(slug[0],slug[1],headSha);String combinedStatus=combinedStatus(slug[0],slug[1],headSha);
        List<String> blockers=new ArrayList<>();
        if(!merged){
            if(!"open".equalsIgnoreCase(state))blockers.add("Pull request is not open");
            if(draft)blockers.add("Pull request is still draft");
            if(Boolean.FALSE.equals(mergeable)||"dirty".equalsIgnoreCase(mergeableState))blockers.add("Pull request has merge conflicts");
            for(Check c:checks){if(!c.completed())blockers.add("Check still running: "+c.name());else if(!c.success())blockers.add("Check failed: "+c.name()+" ("+c.conclusion()+")");}
            if(!combinedStatus.isBlank()&&!Set.of("success","pending").contains(combinedStatus.toLowerCase(Locale.ROOT)))blockers.add("Commit status is "+combinedStatus);
            if("pending".equalsIgnoreCase(combinedStatus))blockers.add("Commit statuses are pending");
        }
        boolean ready=merged||blockers.isEmpty();
        return new RepositoryReadiness(stored.getRepositoryAlias(),stored.getRepositorySlug(),stored.getPullRequestNumber(),stored.getPullRequestUrl(),state,draft,merged,mergeable,mergeableState,headSha,combinedStatus,List.copyOf(checks),ready,List.copyOf(blockers));
    }

    private List<Check> checks(String owner,String repo,String sha){
        if(!StringUtils.hasText(sha))return List.of();JsonNode root=get("/repos/{owner}/{repo}/commits/{sha}/check-runs?per_page=100",owner,repo,sha);
        List<Check> result=new ArrayList<>();for(JsonNode n:root.path("check_runs")){String status=n.path("status").asText();String conclusion=n.path("conclusion").asText("");result.add(new Check(n.path("name").asText(),status,conclusion,"completed".equalsIgnoreCase(status),Set.of("success","neutral","skipped").contains(conclusion.toLowerCase(Locale.ROOT))));}return List.copyOf(result);
    }
    private String combinedStatus(String owner,String repo,String sha){if(!StringUtils.hasText(sha))return "";return get("/repos/{owner}/{repo}/commits/{sha}/status",owner,repo,sha).path("state").asText("");}

    private JsonNode get(String path,Object...vars){
        var request=github.get().uri(path,vars).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28");
        if(StringUtils.hasText(token))request=request.header("Authorization","Bearer "+token);
        JsonNode result=request.retrieve().body(JsonNode.class);if(result==null)throw new IllegalStateException("GitHub returned an empty response for "+path);return result;
    }

    public record Readiness(UUID workItemId,boolean mergeReady,boolean allMerged,List<RepositoryReadiness> repositories,List<String> blockers){}
    public record RepositoryReadiness(String alias,String repository,int pullRequestNumber,String url,String state,boolean draft,boolean merged,Boolean mergeable,String mergeableState,String headSha,String combinedStatus,List<Check> checks,boolean mergeReady,List<String> blockers){}
    public record Check(String name,String status,String conclusion,boolean completed,boolean success){}
}
