package dev.protocollo.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting semplice basato sull'algoritmo "token bucket", applicato per
 * indirizzo IP del chiamante.
 *
 * Ogni client ha un secchiello con una capacita massima di gettoni che si
 * ricarica nel tempo a velocita costante: ogni richiesta consuma un gettone, e
 * quando il secchiello e vuoto si risponde con 429 (Too Many Requests).
 *
 * Implementazione volutamente senza librerie esterne, per restare trasparente.
 * In un sistema distribuito si userebbe uno store condiviso (es. Redis) invece
 * di una mappa in memoria.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final boolean abilitato;
    private final int capacita;
    private final double gettoniPerSecondo;

    /** Un secchiello per ogni IP. */
    private final ConcurrentHashMap<String, Bucket> secchielli = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${app.rate-limit.enabled:true}") boolean abilitato,
            @Value("${app.rate-limit.capacity:100}") int capacita,
            @Value("${app.rate-limit.refill-per-minute:100}") int ricaricaPerMinuto) {
        this.abilitato = abilitato;
        this.capacita = capacita;
        this.gettoniPerSecondo = ricaricaPerMinuto / 60.0;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Non limito documentazione e health check
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!abilitato) {
            filterChain.doFilter(request, response);
            return;
        }

        String client = identificaClient(request);
        Bucket bucket = secchielli.computeIfAbsent(client, k -> new Bucket(capacita));

        if (bucket.provaAConsumare(capacita, gettoniPerSecondo)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit superato per il client {}", client);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Troppe richieste, riprova piu tardi\"}");
        }
    }

    /** Usa l'IP del client; tiene conto di un eventuale proxy (X-Forwarded-For). */
    private String identificaClient(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Secchiello di gettoni. La ricarica e "lazy": calcolata al momento del
     * consumo in base al tempo trascorso dall'ultima operazione.
     */
    private static final class Bucket {

        private double gettoni;
        private long ultimaRicaricaNanos;

        Bucket(int capacitaIniziale) {
            this.gettoni = capacitaIniziale;
            this.ultimaRicaricaNanos = System.nanoTime();
        }

        synchronized boolean provaAConsumare(int capacita, double gettoniPerSecondo) {
            long adesso = System.nanoTime();
            double secondiTrascorsi = (adesso - ultimaRicaricaNanos) / 1_000_000_000.0;
            // Ricarico senza superare la capacita massima
            gettoni = Math.min(capacita, gettoni + secondiTrascorsi * gettoniPerSecondo);
            ultimaRicaricaNanos = adesso;

            if (gettoni >= 1.0) {
                gettoni -= 1.0;
                return true;
            }
            return false;
        }
    }
}
