package dev.protocollo.messaging;

import java.time.Instant;

/**
 * Evento pubblicato su Kafka quando un documento viene creato o aggiornato.
 *
 * E un {@code record}: immutabile e con serializzazione JSON automatica
 * tramite Jackson. Rappresenta il "messaggio di protocollazione" inviato ai
 * sistemi a valle interessati al ciclo di vita del documento.
 */
public record ProtocollazioneEvent(
        Long idDocumento,
        String titolo,
        String numeroProtocollo,
        String proprietario,
        TipoOperazione operazione,
        Instant timestamp) {

    /** Tipo di operazione che ha generato l'evento. */
    public enum TipoOperazione {
        CREAZIONE,
        AGGIORNAMENTO
    }

    public static ProtocollazioneEvent di(TipoOperazione operazione,
                                          Long idDocumento,
                                          String titolo,
                                          String numeroProtocollo,
                                          String proprietario) {
        return new ProtocollazioneEvent(idDocumento, titolo, numeroProtocollo,
                proprietario, operazione, Instant.now());
    }
}
