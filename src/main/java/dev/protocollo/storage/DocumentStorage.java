package dev.protocollo.storage;

/**
 * Astrazione per il salvataggio e il recupero di file binari (es. i PDF).
 *
 * Avere un'interfaccia permette di cambiare il backend senza toccare la logica
 * di business: in profilo "dev" si usa il filesystem locale
 * ({@link LocalFileSystemStorage}), in profilo "prod" un object storage S3
 * ({@link S3ObjectStorage}).
 */
public interface DocumentStorage {

    /**
     * Salva un contenuto binario con la chiave indicata.
     *
     * @param chiave      identificativo logico del file (es. "documenti/PRT-2026-000001.pdf")
     * @param contenuto   byte del file
     * @param contentType tipo MIME (es. "application/pdf")
     * @return il riferimento con cui ritrovare il file (qui coincide con la chiave)
     */
    String salva(String chiave, byte[] contenuto, String contentType);

    /**
     * Recupera il contenuto binario salvato in precedenza.
     *
     * @param chiave la chiave usata in fase di salvataggio
     * @return i byte del file
     */
    byte[] leggi(String chiave);
}
