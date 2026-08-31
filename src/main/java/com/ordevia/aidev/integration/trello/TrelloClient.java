package com.ordevia.aidev.integration.trello;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

@Component
public class TrelloClient {
    private final RestClient client;
    private final TrelloProperties properties;

    public TrelloClient(RestClient.Builder builder,TrelloProperties properties){this.client=builder.baseUrl(properties.baseUrl()).build();this.properties=properties;}

    public Card card(String id){
        requireConfigured();
        JsonNode node=client.get().uri(uri->uri.path("/1/cards/{id}").queryParam("key",properties.apiKey()).queryParam("token",properties.token())
                .queryParam("fields","name,desc,url,shortLink,idList").build(id)).retrieve().body(JsonNode.class);
        if(node==null)throw new IllegalStateException("Trello returned empty card");
        return new Card(node.path("id").asText(),node.path("shortLink").asText(),node.path("name").asText(),node.path("desc").asText(),node.path("url").asText(),node.path("idList").asText());
    }

    public List<Comment> comments(String cardId){
        requireConfigured();
        JsonNode root=client.get().uri(uri->uri.path("/1/cards/{id}/actions").queryParam("key",properties.apiKey()).queryParam("token",properties.token())
                .queryParam("filter","commentCard").queryParam("limit",100).build(cardId)).retrieve().body(JsonNode.class);
        if(root==null||!root.isArray())return List.of();List<Comment> result=new ArrayList<>();
        for(JsonNode action:root){String text=action.path("data").path("text").asText("");String member=action.path("memberCreator").path("fullName").asText(action.path("memberCreator").path("username").asText("unknown"));Instant date=parse(action.path("date").asText(null));result.add(new Comment(action.path("id").asText(),text,member,date));}
        result.sort(Comparator.comparing(Comment::createdAt,Comparator.nullsFirst(Comparator.naturalOrder())));return List.copyOf(result);
    }

    public void addComment(String cardId,String text){
        requireConfigured();
        client.post().uri(uri->uri.path("/1/cards/{id}/actions/comments").queryParam("key",properties.apiKey()).queryParam("token",properties.token()).queryParam("text",text).build(cardId)).retrieve().toBodilessEntity();
    }

    private void requireConfigured(){if(!properties.enabled())throw new IllegalStateException("Trello integration is disabled");if(!StringUtils.hasText(properties.apiKey())||!StringUtils.hasText(properties.token()))throw new IllegalStateException("Trello API key/token are not configured");}
    private Instant parse(String value){try{return value==null?null:Instant.parse(value);}catch(Exception e){return null;}}

    public record Card(String id,String shortLink,String name,String description,String url,String listId){}
    public record Comment(String id,String text,String author,Instant createdAt){}
}
