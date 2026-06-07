package dev.protocollo.domain;

/**
 * Ruoli applicativi assegnati agli utenti.
 *
 * Spring Security, per convenzione, prefissa i ruoli con "ROLE_": il valore
 * {@link #USER} diventa quindi l'authority "ROLE_USER" (vedi UtenteAutenticato).
 */
public enum Ruolo {

    /** Utente standard: puo leggere e creare documenti, e modificare i propri. */
    USER,

    /** Amministratore: puo modificare e protocollare qualsiasi documento. */
    ADMIN
}
