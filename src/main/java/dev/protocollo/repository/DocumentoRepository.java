package dev.protocollo.repository;

import dev.protocollo.domain.Documento;
import dev.protocollo.domain.StatoDocumento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository Spring Data per l'entita {@link Documento}.
 *
 * Mostra diversi modi di interrogare il DB con Hibernate/JPA:
 *  - metodi CRUD ereditati da {@link JpaRepository};
 *  - query derivate dal nome del metodo (countByProprietario);
 *  - query JPQL esplicita con {@link Query};
 *  - query dinamiche con {@link JpaSpecificationExecutor} (filtri combinabili).
 */
public interface DocumentoRepository
        extends JpaRepository<Documento, Long>, JpaSpecificationExecutor<Documento> {

    /**
     * Conta quanti documenti appartengono a un dato proprietario.
     * Usata dal service per generare un numero di protocollo progressivo.
     */
    long countByProprietario(String proprietario);

    /**
     * Ricerca paginata per stato. La paginazione e fornita da Spring Data
     * tramite il parametro {@link Pageable}.
     */
    Page<Documento> findByStato(StatoDocumento stato, Pageable pageable);

    /**
     * Esempio di query JPQL scritta a mano: filtra per testo contenuto nel
     * titolo (case-insensitive) e restituisce una pagina di risultati.
     */
    @Query("""
            select d
            from Documento d
            where lower(d.titolo) like lower(concat('%', :testo, '%'))
            """)
    Page<Documento> cercaPerTitolo(@Param("testo") String testo, Pageable pageable);

    /**
     * Documenti approvati, non ancora accodati per la protocollazione, il cui
     * ritardo configurato e scaduto. Usata dal job di scansione schedulato.
     */
    List<Documento> findByStatoAndProtocollazioneInCodaFalseAndDataApprovazioneLessThanEqual(
            StatoDocumento stato, Instant scadenza);
}
