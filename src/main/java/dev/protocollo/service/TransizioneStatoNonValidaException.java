package dev.protocollo.service;

/**
 * Eccezione lanciata quando si tenta una transizione di stato non consentita
 * per un documento (es. modificarlo dopo l'approvazione, approvarlo due volte).
 * Viene tradotta in una risposta HTTP 409 dal GlobalExceptionHandler.
 */
public class TransizioneStatoNonValidaException extends RuntimeException {

    public TransizioneStatoNonValidaException(String messaggio) {
        super(messaggio);
    }
}
