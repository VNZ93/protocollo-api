package dev.protocollo.messaging;

import dev.protocollo.service.DocumentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka che intercetta gli aggiornamenti provenienti da un indice
 * esterno e li applica ai documenti locali.
 *
 * Esempio di scenario: un motore di indicizzazione, dopo aver rielaborato una
 * risorsa, pubblica un evento per segnalare un cambio di stato; il nostro
 * sistema si allinea di conseguenza.
 */
@Component
public class IndiceConsumer {

    private static final Logger log = LoggerFactory.getLogger(IndiceConsumer.class);

    private final DocumentoService documentoService;

    public IndiceConsumer(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    /**
     * Ascolta il topic degli aggiornamenti dell'indice. Il tipo del messaggio
     * e deserializzato in {@link IndiceAggiornamentoEvent} (vedi configurazione
     * del consumer in application.yml).
     */
    @KafkaListener(
            topics = "${app.kafka.topic-indice}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consuma(IndiceAggiornamentoEvent evento) {
        log.info("Ricevuto aggiornamento dall'indice '{}' per il documento {}",
                evento.origine(), evento.idDocumento());
        documentoService.applicaAggiornamentoIndice(evento);
    }
}
