package dev.protocollo.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro di logging che gira per primo nella catena (precedenza massima).
 *
 * Per ogni richiesta:
 *  - assegna o propaga un identificativo di correlazione (header X-Request-Id);
 *  - lo inserisce nell'MDC cosi che compaia in ogni riga di log della richiesta
 *    (fondamentale per seguire una richiesta su Kibana/ELK);
 *  - registra metodo, URI, stato HTTP e durata.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = ricavaRequestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        long inizio = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durata = System.currentTimeMillis() - inizio;
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durata);
            // Pulisco sempre l'MDC: il thread viene riusato per altre richieste
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /** Riusa l'id passato dal client, se presente, altrimenti ne genera uno nuovo. */
    private String ricavaRequestId(HttpServletRequest request) {
        String fornito = request.getHeader(HEADER_REQUEST_ID);
        return StringUtils.hasText(fornito) ? fornito : UUID.randomUUID().toString();
    }
}
