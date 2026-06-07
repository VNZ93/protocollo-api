package dev.protocollo.domain;

/**
 * Stati possibili nel ciclo di vita di un documento.
 */
public enum StatoDocumento {

    /** Documento appena creato, ancora modificabile liberamente. */
    BOZZA,

    /** Documento a cui e stato assegnato un numero di protocollo. */
    PROTOCOLLATO,

    /** Documento archiviato, non piu operativo. */
    ARCHIVIATO
}
