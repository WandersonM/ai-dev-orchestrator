package com.ordevia.aidev.integration.trello;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="trello_work_item_link")
public class TrelloWorkItemLink {
    @Id private UUID id;
    @Column(name="project_id",nullable=false) private UUID projectId;
    @Column(name="work_item_id",nullable=false,unique=true) private UUID workItemId;
    @Column(name="card_id",nullable=false,unique=true,length=120) private String cardId;
    @Column(name="card_short_link",length=80) private String cardShortLink;
    @Column(name="card_url",columnDefinition="text") private String cardUrl;
    @Column(name="last_question_round",nullable=false) private int lastQuestionRound;
    @Column(name="last_seen_comment_at") private Instant lastSeenCommentAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    protected TrelloWorkItemLink() {}
    public TrelloWorkItemLink(UUID id,UUID projectId,UUID workItemId,String cardId,String shortLink,String cardUrl){
        this.id=id;this.projectId=projectId;this.workItemId=workItemId;this.cardId=cardId;this.cardShortLink=shortLink;this.cardUrl=cardUrl;
        this.createdAt=Instant.now();this.updatedAt=createdAt;
    }
    public void markQuestionsPublished(int round){lastQuestionRound=Math.max(lastQuestionRound,round);updatedAt=Instant.now();}
    public void seenUntil(Instant at){if(at!=null&&(lastSeenCommentAt==null||at.isAfter(lastSeenCommentAt)))lastSeenCommentAt=at;updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public UUID getWorkItemId(){return workItemId;} public String getCardId(){return cardId;}
    public String getCardShortLink(){return cardShortLink;} public String getCardUrl(){return cardUrl;} public int getLastQuestionRound(){return lastQuestionRound;}
    public Instant getLastSeenCommentAt(){return lastSeenCommentAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
