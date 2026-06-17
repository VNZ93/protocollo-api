package dev.protocollo.client;

import java.util.List;

/**
 * Dati di profilo "puliti" restituiti al nostro dominio, ricavati dalla
 * risposta (potenzialmente variabile) del microservizio esterno.
 *
 * Mappare la risposta esterna su un nostro DTO stabile e cio che ci protegge
 * dai cambiamenti dell'altro servizio: il resto dell'applicazione dipende da
 * questo record, non dal formato JSON remoto.
 */
public record DatiProfilo(
        String username,
        String nomeCompleto,
        String email,
        String telefono,
        List<String> gruppi) {
}
