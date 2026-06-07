package dev.protocollo.repository;

import dev.protocollo.domain.Utente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository Spring Data per l'entita {@link Utente}.
 *
 * Estendendo {@link JpaRepository} si ottengono gratuitamente le operazioni
 * CRUD di base; qui aggiungiamo solo una query derivata dal nome del metodo.
 */
public interface UtenteRepository extends JpaRepository<Utente, Long> {

    /**
     * Cerca un utente per username.
     * Spring Data genera automaticamente la query a partire dal nome del metodo.
     */
    Optional<Utente> findByUsername(String username);
}
