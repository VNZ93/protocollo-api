package dev.protocollo.domain;

/**
 * Stati possibili nel ciclo di vita di un documento.
 */
public enum StatoDocumento {

    /** Documento appena creato, ancora modificabile liberamente. */
    BOZZA,

    /** Documento approvato da un amministratore, in attesa di protocollazione automatica. */
    APPROVATA,

    /** Documento a cui e stato assegnato un numero di protocollo. */
    PROTOCOLLATO
}
