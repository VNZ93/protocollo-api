package dev.protocollo.service;

/**
 * Eccezione lanciata quando una risorsa richiesta non esiste.
 * Viene tradotta in una risposta HTTP 404 dal GlobalExceptionHandler.
 */
public class RisorsaNonTrovataException extends RuntimeException {

    public RisorsaNonTrovataException(String messaggio) {
        super(messaggio);
    }
}
