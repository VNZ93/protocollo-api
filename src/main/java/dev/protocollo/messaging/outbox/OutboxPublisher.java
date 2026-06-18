package dev.protocollo.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.protocollo.domain.OutboxEvent;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.messaging.RichiestaProtocollazioneEvent;
import dev.protocollo.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Publisher dell'outbox: a intervalli regolari legge gli eventi non ancora
 * pubblicati e li invia a Kafka, marcandoli come pubblicati (pattern Outbox).
 *
 * Garantisce una consegna "at-least-once": un evento viene marcato pubblicato
 * solo dopo l'invio confermato; se l'app si arresta tra invio e commit, al
 * riavvio l'evento viene reinviato (i consumer devono essere idempotenti).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Pubblica gli eventi in sospeso. Il metodo e transazionale: le entita
     * lette restano "managed" e il loro stato (pubblicato) viene salvato al
     * commit grazie al dirty checking di Hibernate.
     */
    @Scheduled(fixedDelayString = "${app.outbox.polling-delay:5000}")
    @Transactional
    public void pubblicaInSospeso() {
        List<OutboxEvent> inSospeso = outboxRepository.findTop50ByPubblicatoFalseOrderByIdAsc();
        if (inSospeso.isEmpty()) {
            return;
        }
        log.debug("Outbox: {} eventi da pubblicare", inSospeso.size());

        for (OutboxEvent evento : inSospeso) {
            try {
                Object payload = deserializza(evento);
                // Invio sincrono (.get): marco "pubblicato" solo se va a buon fine
                kafkaTemplate.send(evento.getTopic(), evento.getChiave(), payload).get();
                evento.segnaPubblicato();
                log.info("Outbox: evento {} pubblicato su {}", evento.getId(), evento.getTopic());
            } catch (Exception e) {
                // L'evento resta non pubblicato e verra ritentato al prossimo giro
                log.error("Outbox: invio evento {} fallito, verra ritentato: {}",
                        evento.getId(), e.getMessage());
            }
        }
    }

    /**
     * Ricostruisce l'oggetto evento dal payload JSON in base al tipo.
     * Con piu tipi di evento si userebbe una mappa tipo -> classe.
     */
    private Object deserializza(OutboxEvent evento) throws Exception {
        if (OutboxService.TIPO_PROTOCOLLAZIONE.equals(evento.getTipo())) {
            return objectMapper.readValue(evento.getPayload(), ProtocollazioneEvent.class);
        }
        if (OutboxService.TIPO_RICHIESTA_PROTOCOLLAZIONE.equals(evento.getTipo())) {
            return objectMapper.readValue(evento.getPayload(), RichiestaProtocollazioneEvent.class);
        }
        throw new IllegalStateException("Tipo di evento outbox sconosciuto: " + evento.getTipo());
    }
}
