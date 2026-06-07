package dev.protocollo.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.protocollo.domain.OutboxEvent;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra gli eventi nella tabella outbox (pattern Outbox).
 *
 * Scrivere l'evento nello stesso database e nella stessa transazione del
 * cambiamento di business risolve il "dual write problem": evita cioe che il
 * dato venga salvato ma l'evento Kafka vada perso (o viceversa). La consegna
 * effettiva su Kafka avviene poi in modo asincrono ({@link OutboxPublisher}).
 */
@Service
public class OutboxService {

    /** Tipo logico usato per riconoscere e deserializzare l'evento. */
    public static final String TIPO_PROTOCOLLAZIONE = "PROTOCOLLAZIONE";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String topicProtocollazione;

    public OutboxService(OutboxEventRepository outboxRepository,
                         ObjectMapper objectMapper,
                         @Value("${app.kafka.topic-protocollazione}") String topicProtocollazione) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.topicProtocollazione = topicProtocollazione;
    }

    /**
     * Registra un evento di protocollazione nell'outbox.
     *
     * {@code Propagation.MANDATORY}: il metodo DEVE essere chiamato dentro una
     * transazione gia aperta (quella del service che modifica il documento),
     * cosi da garantire l'atomicita tra dato ed evento.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registraProtocollazione(ProtocollazioneEvent evento) {
        try {
            String payload = objectMapper.writeValueAsString(evento);
            String aggregateId = String.valueOf(evento.idDocumento());
            OutboxEvent outbox = new OutboxEvent(
                    TIPO_PROTOCOLLAZIONE, aggregateId, topicProtocollazione, aggregateId, payload);
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serializzazione evento outbox fallita", e);
        }
    }
}
