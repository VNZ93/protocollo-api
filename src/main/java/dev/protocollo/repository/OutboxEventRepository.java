package dev.protocollo.repository;

import dev.protocollo.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository per gli eventi outbox.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Recupera i primi eventi non ancora pubblicati, in ordine di inserimento.
     * Il limite evita di caricare in memoria un backlog troppo grande.
     */
    List<OutboxEvent> findTop50ByPubblicatoFalseOrderByIdAsc();
}
