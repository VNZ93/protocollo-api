package dev.protocollo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Proprieta configurabili legate all'accreditamento (prefisso {@code app.accreditamento}).
 *
 * Esempio di binding type-safe della configurazione: invece di leggere singoli
 * valori con {@code @Value}, si mappa un'intera sezione di application.yml in un
 * oggetto immutabile. La lista dei servizi finisce nel PDF di accreditamento.
 */
@ConfigurationProperties(prefix = "app.accreditamento")
public record AccreditamentoProperties(List<String> servizi) {

    /** Restituisce i servizi configurati, oppure una lista vuota se assenti. */
    public List<String> serviziSicuri() {
        return servizi != null ? servizi : List.of();
    }
}
