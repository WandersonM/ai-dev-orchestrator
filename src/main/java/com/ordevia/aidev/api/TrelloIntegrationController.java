package com.ordevia.aidev.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ordevia.aidev.integration.trello.TrelloSyncService;
import com.ordevia.aidev.integration.trello.TrelloWorkItemLink;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/integrations/trello")
public class TrelloIntegrationController {
    private final TrelloSyncService trello;
    public TrelloIntegrationController(TrelloSyncService trello){this.trello=trello;}

    @PostMapping("/projects/{projectId}/cards/{cardId}/import")
    public TrelloWorkItemLink importCard(@PathVariable UUID projectId,@PathVariable String cardId){return trello.importCard(projectId,cardId);}

    @PostMapping("/work-items/{workItemId}/sync")
    public TrelloSyncService.SyncResult sync(@PathVariable UUID workItemId){return trello.syncWorkItem(workItemId);}

    @RequestMapping(value="/webhook", method=RequestMethod.HEAD)
    public ResponseEntity<Void> verifyWebhook(){return ResponseEntity.ok().build();}

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody JsonNode payload){
        String cardId = firstText(
                payload.path("action").path("data").path("card").path("id"),
                payload.path("model").path("id"));
        if(cardId==null||cardId.isBlank()) return ResponseEntity.accepted().body(new WebhookResult(false,null,"No card id in event"));
        Optional<TrelloSyncService.SyncResult> result=trello.syncCard(cardId);
        return ResponseEntity.accepted().body(result.<Object>map(r->new WebhookResult(true,r,"synced")).orElseGet(()->new WebhookResult(false,null,"Card is not linked")));
    }

    private String firstText(JsonNode... nodes){
        for(JsonNode node:nodes) if(node!=null&&!node.isMissingNode()&&!node.isNull()&&!node.asText().isBlank()) return node.asText();
        return null;
    }

    public record WebhookResult(boolean linked, TrelloSyncService.SyncResult result, String message){}
}
