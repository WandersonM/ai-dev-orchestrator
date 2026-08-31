package com.ordevia.aidev.api;

import com.ordevia.aidev.integration.trello.TrelloSyncService;
import com.ordevia.aidev.integration.trello.TrelloWorkItemLink;
import org.springframework.web.bind.annotation.*;

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
}
