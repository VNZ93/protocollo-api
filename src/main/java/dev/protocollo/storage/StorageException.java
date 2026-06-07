package dev.protocollo.storage;

/**
 * Errore generico durante il salvataggio o la lettura su storage.
 */
public class StorageException extends RuntimeException {

    public StorageException(String messaggio, Throwable causa) {
        super(messaggio, causa);
    }
}
