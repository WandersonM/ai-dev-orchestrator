package com.ordevia.aidev.integration.trello;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrelloWorkItemLinkJpaRepository extends JpaRepository<TrelloWorkItemLink, UUID> {
    Optional<TrelloWorkItemLink> findByCardId(String cardId);
    Optional<TrelloWorkItemLink> findByWorkItemId(UUID workItemId);
    List<TrelloWorkItemLink> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
