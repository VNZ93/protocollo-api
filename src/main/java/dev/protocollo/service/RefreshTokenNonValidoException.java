package dev.protocollo.service;

/**
 * Eccezione lanciata quando un refresh token e inesistente, scaduto o revocato.
 * Viene tradotta in una risposta HTTP 401 dal GlobalExceptionHandler.
 */
public class RefreshTokenNonValidoException extends RuntimeException {

    public RefreshTokenNonValidoException(String messaggio) {
        super(messaggio);
    }
}
