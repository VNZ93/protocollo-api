package dev.protocollo.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client verso un microservizio esterno di profilo (ipotetico).
 *
 * Il punto interessante e la mappatura "difensiva" del JSON: invece di legarci
 * a una classe che rispecchia esattamente la risposta remota, leggiamo un
 * {@link JsonNode} e ne estraiamo i campi con {@code path()} e valori di default.
 * Cosi, se il servizio esterno rinomina o sposta un campo, qui ce ne accorgiamo
 * in un solo punto e ci adattiamo, senza rompere tutto il resto dell'applicazione.
 */
@Component
public class ProfiloClient {

    private static final Logger log = LoggerFactory.getLogger(ProfiloClient.class);

    private final RestClient restClient;

    public ProfiloClient(RestClient profiloRestClient) {
        this.restClient = profiloRestClient;
    }

    /**
     * Recupera i dati di profilo di un utente dal servizio esterno.
     *
     * @return i dati mappati, oppure {@code Optional.empty()} se l'utente non
     *         esiste (404). Gli altri errori di rete vengono propagati e
     *         tradotti in 502 dal GlobalExceptionHandler.
     */
    public Optional<DatiProfilo> recuperaPerUsername(String username) {
        try {
            JsonNode root = restClient.get()
                    .uri("/profilo/{username}", username)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null || root.isNull() || root.isMissingNode()) {
                return Optional.empty();
            }
            return Optional.of(mappa(username, root));
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Profilo esterno: utente '{}' non trovato", username);
            return Optional.empty();
        }
    }

    /**
     * Mappa il JSON remoto sul nostro DTO, tollerando nomi di campo alternativi
     * e strutture annidate. Nessun accesso diretto che possa lanciare NPE:
     * {@code path()} restituisce un nodo "mancante" invece di null.
     */
    private DatiProfilo mappa(String username, JsonNode node) {
        // Il nome completo puo arrivare gia pronto, oppure come nome + cognome
        // con diverse possibili etichette (italiano/inglese).
        String nomeCompleto = primoNonVuoto(
                node.path("nomeCompleto").asText(""),
                unisci(node.path("nome").asText(""), node.path("cognome").asText("")),
                unisci(node.path("firstName").asText(""), node.path("lastName").asText("")),
                node.path("name").asText(""));

        // L'email puo essere in radice o annidata in un oggetto "contatti".
        String email = primoNonVuoto(
                node.path("email").asText(""),
                node.path("mail").asText(""),
                node.path("contatti").path("email").asText(""));

        String telefono = primoNonVuoto(
                node.path("telefono").asText(""),
                node.path("phone").asText(""),
                node.path("contatti").path("telefono").asText(""));

        // I gruppi possono chiamarsi in modi diversi ed essere assenti.
        List<String> gruppi = leggiArray(node, "gruppi", "groups", "ruoli");

        return new DatiProfilo(username, nomeCompleto, email, telefono, gruppi);
    }

    /** Restituisce il primo valore non vuoto tra quelli passati ("" se nessuno). */
    private String primoNonVuoto(String... valori) {
        for (String valore : valori) {
            if (valore != null && !valore.isBlank()) {
                return valore;
            }
        }
        return "";
    }

    private String unisci(String parte1, String parte2) {
        return (parte1 + " " + parte2).trim();
    }

    /** Cerca il primo campo che sia un array tra i nomi indicati e lo legge. */
    private List<String> leggiArray(JsonNode node, String... possibiliNomi) {
        List<String> valori = new ArrayList<>();
        for (String nome : possibiliNomi) {
            JsonNode candidato = node.path(nome);
            if (candidato.isArray()) {
                candidato.forEach(elemento -> valori.add(elemento.asText()));
                break;
            }
        }
        return valori;
    }
}
