package dev.protocollo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Dichiarazione del topic Kafka usato per gli eventi di protocollazione.
 *
 * Esponendo un bean {@link NewTopic}, Spring Kafka (tramite l'AdminClient)
 * crea automaticamente il topic all'avvio se non esiste gia. In produzione i
 * topic sono spesso creati dagli operatori, ma per un esempio locale e comodo.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic topicProtocollazione(
            @Value("${app.kafka.topic-protocollazione}") String nomeTopic) {
        return TopicBuilder.name(nomeTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
