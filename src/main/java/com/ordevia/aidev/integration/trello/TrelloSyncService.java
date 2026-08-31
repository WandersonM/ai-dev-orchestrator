package com.ordevia.aidev.integration.trello;

import com.ordevia.aidev.planning.application.PlanningService;
import com.ordevia.aidev.planning.domain.PlanningQuestion;
import com.ordevia.aidev.planning.domain.PlanningStatus;
import com.ordevia.aidev.project.application.ProjectService;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TrelloSyncService {
    private static final String ANSWER="AIDEV ANSWER ";
    private static final String APPROVE="AIDEV APPROVE";
    private static final String CHANGES="AIDEV CHANGES";

    private final TrelloClient trello;
    private final TrelloProperties properties;
    private final TrelloWorkItemLinkJpaRepository links;
    private final WorkItemJpaRepository workItems;
    private final ProjectService projects;
    private final PlanningService planning;

    public TrelloSyncService(TrelloClient trello,TrelloProperties properties,TrelloWorkItemLinkJpaRepository links,
                             WorkItemJpaRepository workItems,ProjectService projects,PlanningService planning){
        this.trello=trello;this.properties=properties;this.links=links;this.workItems=workItems;this.projects=projects;this.planning=planning;
    }

    @Transactional
    public TrelloWorkItemLink importCard(UUID projectId,String cardId){
        Optional<TrelloWorkItemLink> existing=links.findByCardId(cardId);if(existing.isPresent())return existing.get();
        TrelloClient.Card card=trello.card(cardId);
        WorkItem item=projects.addWorkItem(projectId,"TRELLO-"+card.shortLink(),card.name(),card.description(),List.of());
        TrelloWorkItemLink link=links.save(new TrelloWorkItemLink(UUID.randomUUID(),projectId,item.getId(),card.id(),card.shortLink(),card.url()));
        trello.addComment(card.id(),"[AIDEV] Card imported as WorkItem `"+item.getId()+"`. Planning will start when the WorkItem is executed.");
        return link;
    }

    public SyncResult syncWorkItem(UUID workItemId){
        TrelloWorkItemLink link=links.findByWorkItemId(workItemId).orElseThrow(()->new NoSuchElementException("WorkItem is not linked to Trello"));
        int consumed=consumeCommands(link);
        int published=publishPlanningState(link);
        return new SyncResult(workItemId,link.getCardId(),consumed,published,workItems.findById(workItemId).orElseThrow().getStatus());
    }

    @Scheduled(fixedDelayString="${aidev.trello.polling-interval-ms:30000}")
    public void poll(){
        if(!properties.enabled()||!properties.pollingEnabled())return;
        for(TrelloWorkItemLink link:links.findAll()){
            try{syncWorkItem(link.getWorkItemId());}catch(Exception ignored){}
        }
    }

    @Transactional
    protected int consumeCommands(TrelloWorkItemLink link){
        List<TrelloClient.Comment> comments=trello.comments(link.getCardId());int consumed=0;Instant newest=link.getLastSeenCommentAt();
        for(TrelloClient.Comment comment:comments){
            if(comment.createdAt()!=null&&link.getLastSeenCommentAt()!=null&&!comment.createdAt().isAfter(link.getLastSeenCommentAt()))continue;
            String text=comment.text()==null?"":comment.text().trim();
            try{
                if(text.regionMatches(true,0,ANSWER,0,ANSWER.length())){consumeAnswer(link,text,comment.author());consumed++;}
                else if(text.equalsIgnoreCase(APPROVE)){consumeApprove(link);consumed++;}
                else if(text.toUpperCase(Locale.ROOT).startsWith(CHANGES)){consumeChanges(link,text,comment.author());consumed++;}
            }catch(IllegalStateException|IllegalArgumentException ignored){}
            if(comment.createdAt()!=null&&(newest==null||comment.createdAt().isAfter(newest)))newest=comment.createdAt();
        }
        link.seenUntil(newest);links.save(link);
        WorkItem item=workItems.findById(link.getWorkItemId()).orElseThrow();
        if(item.getStatus()==WorkItemStatus.WAITING_FOR_USER_INPUT){
            try{var view=planning.get(item.getId());if(view.unansweredBlockingQuestions()==0)planning.continuePlanning(item.getId());}catch(Exception ignored){}
        }
        return consumed;
    }

    @Transactional
    protected int publishPlanningState(TrelloWorkItemLink link){
        WorkItem item=workItems.findById(link.getWorkItemId()).orElseThrow();
        if(item.getStatus()==WorkItemStatus.WAITING_FOR_USER_INPUT){
            var view=planning.get(item.getId());int round=view.session().getRound();if(round<=link.getLastQuestionRound())return 0;
            List<PlanningQuestion> questions=view.questions().stream().filter(q->q.getRound()==round).toList();
            StringBuilder text=new StringBuilder("[AIDEV][PLANNING][ROUND ").append(round).append("]\nPreciso confirmar estes pontos antes de seguir:\n\n");
            for(PlanningQuestion q:questions){text.append("- `").append(q.getId()).append("` ").append(q.getQuestion()).append(q.isBlocking()?" **(bloqueante)**":"").append("\n  Motivo: ").append(q.getRationale()).append("\n");}
            text.append("\nResponda em um novo comentário usando:\n`AIDEV ANSWER <questionId> <resposta>`");
            trello.addComment(link.getCardId(),text.toString());link.markQuestionsPublished(round);links.save(link);return questions.size();
        }
        if(item.getStatus()==WorkItemStatus.READY_FOR_PLANNING_REVIEW){
            var view=planning.get(item.getId());int marker=10_000+view.session().getRound();if(link.getLastQuestionRound()>=marker)return 0;
            String spec=view.session().getFinalSpecification();if(spec!=null&&spec.length()>8000)spec=spec.substring(0,8000)+"\n...[truncated]";
            trello.addComment(link.getCardId(),"[AIDEV][PLANNING READY]\n\n"+Objects.toString(spec,"")+"\n\nPara aprovar: `AIDEV APPROVE`\nPara pedir ajuste: `AIDEV CHANGES <feedback>`");
            link.markQuestionsPublished(marker);links.save(link);return 1;
        }
        return 0;
    }

    private void consumeAnswer(TrelloWorkItemLink link,String text,String author){
        String remainder=text.substring(ANSWER.length()).trim();int split=remainder.indexOf(' ');if(split<=0)throw new IllegalArgumentException("Answer command must contain question id and answer");
        UUID questionId=UUID.fromString(remainder.substring(0,split).trim());String answer=remainder.substring(split+1).trim();if(answer.isBlank())throw new IllegalArgumentException("Answer cannot be blank");
        planning.answer(link.getWorkItemId(),questionId,answer,"Trello:"+author);
    }
    private void consumeApprove(TrelloWorkItemLink link){planning.approve(link.getWorkItemId());trello.addComment(link.getCardId(),"[AIDEV] Planejamento aprovado. O WorkItem foi liberado para validação de domínio e arquitetura.");}
    private void consumeChanges(TrelloWorkItemLink link,String text,String author){String feedback=text.substring(CHANGES.length()).trim();if(feedback.isBlank())throw new IllegalArgumentException("Changes command requires feedback");planning.requestChanges(link.getWorkItemId(),feedback,"Trello:"+author);}

    public record SyncResult(UUID workItemId,String cardId,int commandsConsumed,int planningMessagesPublished,WorkItemStatus status){}
}
