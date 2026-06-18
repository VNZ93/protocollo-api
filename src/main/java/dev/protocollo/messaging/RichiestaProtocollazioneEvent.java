package dev.protocollo.messaging;

import java.time.Instant;

/**
 * Messaggio "comando" pubblicato dal job di scansione quando un documento
 * approvato ha superato il ritardo configurato: chiede al consumer dedicato
 * di eseguire davvero la protocollazione (numero, PDF, cambio stato).
 *
 * E volutamente distinto da {@link ProtocollazioneEvent}, che invece e un
 * evento illustrativo pubblicato a cose fatte: questo viaggia su un topic
 * diverso, pensato per essere consumato (coda di lavoro), non solo osservato.
 */
public record RichiestaProtocollazioneEvent(Long idDocumento, Instant timestamp) {

    public static RichiestaProtocollazioneEvent di(Long idDocumento) {
        return new RichiestaProtocollazioneEvent(idDocumento, Instant.now());
    }
}
