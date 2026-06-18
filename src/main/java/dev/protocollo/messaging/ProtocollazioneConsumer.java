package dev.protocollo.messaging;

import dev.protocollo.service.DocumentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka della coda di lavoro della protocollazione automatica.
 *
 * A differenza del topic illustrativo {@code topic-protocollazione} (solo
 * osservabile, nessun consumer interno), questo topic e una vera coda di
 * comandi: il job di scansione in {@code DocumentoService} ci scrive una
 * richiesta, e questo consumer esegue davvero la protocollazione.
 */
@Component
public class ProtocollazioneConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProtocollazioneConsumer.class);

    private final DocumentoService documentoService;

    public ProtocollazioneConsumer(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic-protocollazione-lavoro}",
            groupId = "protocollo-protocollazione",
            containerFactory = "protocollazioneListenerContainerFactory")
    public void consuma(RichiestaProtocollazioneEvent evento) {
        log.info("Ricevuta richiesta di protocollazione per il documento {}", evento.idDocumento());
        documentoService.eseguiProtocollazione(evento.idDocumento());
    }
}
