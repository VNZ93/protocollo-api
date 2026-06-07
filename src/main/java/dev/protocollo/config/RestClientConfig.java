package dev.protocollo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configura il {@link RestClient} usato per chiamare il microservizio esterno
 * di anagrafica. L'URL di base e configurabile, cosi da puntare ad ambienti
 * diversi senza modificare il codice.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient anagraficaRestClient(
            RestClient.Builder builder,
            @Value("${app.servizio-anagrafica.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
