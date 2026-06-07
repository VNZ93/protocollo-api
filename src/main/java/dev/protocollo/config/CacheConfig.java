package dev.protocollo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Abilita e configura la cache applicativa (in memoria, con Caffeine).
 *
 * Viene usata sulle GET del singolo documento ({@code @Cacheable}) e invalidata
 * agli aggiornamenti ({@code @CacheEvict}) nel {@code DocumentoService}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Nome della cache dei documenti, riusato nelle annotazioni del service. */
    public static final String CACHE_DOCUMENTI = "documenti";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_DOCUMENTI);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // Numero massimo di voci tenute in cache
                .maximumSize(500)
                // Scadenza: una voce viene rimossa 5 minuti dopo la scrittura
                .expireAfterWrite(Duration.ofMinutes(5)));
        return cacheManager;
    }
}
