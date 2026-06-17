package dev.protocollo.web;

import dev.protocollo.service.RefreshTokenNonValidoException;
import dev.protocollo.service.RisorsaNonTrovataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Gestione centralizzata delle eccezioni per tutti i controller REST.
 *
 * Traduce le eccezioni applicative in risposte HTTP coerenti usando
 * {@link ProblemDetail} (formato RFC 7807, "application/problem+json"),
 * evitando di ripetere la gestione degli errori in ogni controller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Risorsa inesistente: 404 Not Found. */
    @ExceptionHandler(RisorsaNonTrovataException.class)
    public ProblemDetail gestisciNonTrovata(RisorsaNonTrovataException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Permessi insufficienti: 403 Forbidden. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail gestisciAccessoNegato(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Credenziali errate al login: 401 Unauthorized. */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail gestisciCredenzialiErrate(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenziali non valide");
    }

    /** Refresh token mancante, scaduto o revocato: 401 Unauthorized. */
    @ExceptionHandler(RefreshTokenNonValidoException.class)
    public ProblemDetail gestisciRefreshNonValido(RefreshTokenNonValidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** Errori di validazione dei DTO in input: 400 Bad Request con i dettagli. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail gestisciValidazione(MethodArgumentNotValidException ex) {
        String dettagli = ex.getBindingResult().getFieldErrors().stream()
                .map(errore -> errore.getField() + ": " + errore.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, dettagli);
    }

    /** Parametro di richiesta con tipo non valido (es. stato inesistente): 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail gestisciTipoParametroErrato(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Valore non valido per il parametro '" + ex.getName() + "'");
    }

    /** Errore nel chiamare un servizio esterno (es. profilo): 502 Bad Gateway. */
    @ExceptionHandler(RestClientException.class)
    public ProblemDetail gestisciServizioEsterno(RestClientException ex) {
        log.warn("Chiamata a servizio esterno fallita: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Servizio esterno non raggiungibile");
    }

    /** Rete di sicurezza: qualsiasi errore non previsto diventa un 500. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail gestisciGenerico(Exception ex) {
        // Logghiamo lo stack trace completo, ma non lo esponiamo al client
        log.error("Errore non gestito", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Si e verificato un errore interno");
    }
}
