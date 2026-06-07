package dev.protocollo.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer Kafka per gli eventi di protocollazione.
 *
 * L'invio e asincrono e non bloccante: se il broker non e raggiungibile la
 * richiesta HTTP dell'utente non fallisce, l'errore viene solo registrato nei
 * log. Questo rende l'esempio eseguibile anche senza un broker Kafka attivo.
 */
@Component
public class ProtocollazioneProducer {

    private static final Logger log = LoggerFactory.getLogger(ProtocollazioneProducer.class);

    private final KafkaTemplate<String, ProtocollazioneEvent> kafkaTemplate;
    private final String topic;

    public ProtocollazioneProducer(
            KafkaTemplate<String, ProtocollazioneEvent> kafkaTemplate,
            @Value("${app.kafka.topic-protocollazione}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Pubblica un evento sul topic di protocollazione.
     *
     * Come chiave del messaggio si usa l'id del documento: cosi tutti gli
     * eventi relativi allo stesso documento finiscono nella stessa partizione
     * e ne viene preservato l'ordine.
     */
    public void pubblica(ProtocollazioneEvent evento) {
        String chiave = String.valueOf(evento.idDocumento());

        kafkaTemplate.send(topic, chiave, evento)
                .whenComplete((risultato, errore) -> {
                    if (errore != null) {
                        log.error("Invio evento di protocollazione fallito per il documento {}: {}",
                                evento.idDocumento(), errore.getMessage());
                    } else {
                        log.info("Evento di protocollazione pubblicato: documento={}, operazione={}",
                                evento.idDocumento(), evento.operazione());
                    }
                });
    }
}
