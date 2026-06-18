package dev.protocollo.config;

import dev.protocollo.messaging.RichiestaProtocollazioneEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Il consumer Kafka autoconfigurato da Spring Boot (vedi {@code spring.kafka.consumer}
 * in application.yml) ha un solo tipo JSON di default, usato dal topic
 * dell'indice esterno. Il topic di lavoro della protocollazione automatica
 * porta un payload diverso ({@link RichiestaProtocollazioneEvent}) e un
 * group-id proprio: gli serve quindi una ConsumerFactory/ContainerFactory
 * dedicata, costruita riusando le proprieta comuni (bootstrap-servers,
 * ErrorHandlingDeserializer, trusted packages) e sovrascrivendo solo
 * group-id e tipo di default.
 *
 * {@code KafkaProperties.buildConsumerProperties} non basta da solo: il
 * bootstrap-servers effettivo (es. quello assegnato da Testcontainers nei
 * test) viene applicato da Spring Boot tramite {@link KafkaConnectionDetails},
 * non tramite {@code KafkaProperties}. Va quindi riapplicato esplicitamente,
 * altrimenti questa ConsumerFactory dedicata finisce per puntare al default
 * "localhost:9092" invece del broker reale.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, RichiestaProtocollazioneEvent> protocollazioneConsumerFactory(
            KafkaProperties kafkaProperties, KafkaConnectionDetails connectionDetails) {
        Map<String, Object> proprieta = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        proprieta.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumerBootstrapServers());
        proprieta.put(ConsumerConfig.GROUP_ID_CONFIG, "protocollo-protocollazione");
        proprieta.put(JsonDeserializer.VALUE_DEFAULT_TYPE, RichiestaProtocollazioneEvent.class.getName());
        return new DefaultKafkaConsumerFactory<>(proprieta);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RichiestaProtocollazioneEvent> protocollazioneListenerContainerFactory(
            ConsumerFactory<String, RichiestaProtocollazioneEvent> protocollazioneConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, RichiestaProtocollazioneEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(protocollazioneConsumerFactory);
        return factory;
    }
}
