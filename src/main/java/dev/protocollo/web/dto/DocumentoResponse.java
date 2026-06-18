package dev.protocollo.web.dto;

import dev.protocollo.domain.Documento;
import dev.protocollo.domain.StatoDocumento;

import java.time.Instant;

/**
 * Rappresentazione di un documento esposta verso l'esterno.
 *
 * Tenere un DTO separato dall'entita JPA evita di esporre direttamente il
 * modello di persistenza e da pieno controllo su quali campi vengono serializzati.
 */
public record DocumentoResponse(
        Long id,
        String titolo,
        String contenuto,
        StatoDocumento stato,
        String numeroProtocollo,
        String proprietario,
        String pdfRiferimento,
        boolean indicizzato,
        Instant dataIndicizzazione,
        Instant dataCreazione,
        Instant dataAggiornamento,
        boolean archiviato,
        Instant dataApprovazione) {

    /** Costruisce il DTO a partire dall'entita di dominio. */
    public static DocumentoResponse da(Documento documento) {
        return new DocumentoResponse(
                documento.getId(),
                documento.getTitolo(),
                documento.getContenuto(),
                documento.getStato(),
                documento.getNumeroProtocollo(),
                documento.getProprietario(),
                documento.getPdfRiferimento(),
                documento.isIndicizzato(),
                documento.getDataIndicizzazione(),
                documento.getDataCreazione(),
                documento.getDataAggiornamento(),
                documento.isArchiviato(),
                documento.getDataApprovazione());
    }
}
