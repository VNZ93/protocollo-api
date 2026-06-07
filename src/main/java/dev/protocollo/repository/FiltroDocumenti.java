package dev.protocollo.repository;

import dev.protocollo.domain.StatoDocumento;

import java.time.Instant;

/**
 * Insieme dei criteri di ricerca per i documenti. Tutti i campi sono
 * facoltativi: quelli valorizzati vengono combinati in AND (vedi
 * {@link DocumentoSpecifications}).
 *
 * @param stato        filtra per stato del documento
 * @param proprietario filtra per username del proprietario
 * @param testo        testo da cercare nel titolo (case-insensitive)
 * @param creatoDa     data minima di creazione (inclusa)
 * @param creatoA      data massima di creazione (inclusa)
 */
public record FiltroDocumenti(
        StatoDocumento stato,
        String proprietario,
        String testo,
        Instant creatoDa,
        Instant creatoA) {
}
