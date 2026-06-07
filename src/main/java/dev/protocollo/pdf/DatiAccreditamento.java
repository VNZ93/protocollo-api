package dev.protocollo.pdf;

import java.time.Instant;
import java.util.List;

/**
 * Dati che alimentano il template del PDF di accreditamento.
 *
 * Tiene il generatore di PDF disaccoppiato dalle entita JPA: il service
 * costruisce questo modello, il {@link DocumentoPdfService} lo trasforma in PDF.
 *
 * @param titolo           titolo del documento
 * @param numeroProtocollo numero di protocollo assegnato
 * @param nomeCompleto     nome e cognome dell'utente proprietario
 * @param email            email dell'utente
 * @param proprietario     username del proprietario
 * @param dataCreazione    data di creazione del documento
 * @param contenuto        testo libero del documento
 * @param servizi          servizi a cui l'utente risulta accreditato
 */
public record DatiAccreditamento(
        String titolo,
        String numeroProtocollo,
        String nomeCompleto,
        String email,
        String proprietario,
        Instant dataCreazione,
        String contenuto,
        List<String> servizi) {
}
