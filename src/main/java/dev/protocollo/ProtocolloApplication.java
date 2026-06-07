package dev.protocollo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto di ingresso dell'applicazione.
 *
 * L'annotazione {@code @SpringBootApplication} attiva:
 *  - la configurazione automatica (auto-configuration) di Spring Boot;
 *  - la scansione dei componenti a partire da questo package e dai sottostanti;
 *  - la possibilita di definire bean direttamente in questa classe.
 *
 * {@code @EnableSpringDataWebSupport} con modalita VIA_DTO serializza le pagine
 * ({@code Page}) in una struttura JSON stabile e documentata, invece di esporre
 * direttamente l'oggetto interno {@code PageImpl}.
 *
 * {@code @ConfigurationPropertiesScan} registra le classi {@code @ConfigurationProperties}.
 * {@code @EnableScheduling} abilita i task pianificati (es. il publisher dell'outbox).
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
@ConfigurationPropertiesScan
@EnableScheduling
public class ProtocolloApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProtocolloApplication.class, args);
    }
}
