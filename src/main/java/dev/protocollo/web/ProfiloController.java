package dev.protocollo.web;

import dev.protocollo.client.ProfiloClient;
import dev.protocollo.client.DatiProfilo;
import dev.protocollo.service.RisorsaNonTrovataException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espone i dati di profilo recuperati da un microservizio esterno.
 *
 * Mostra l'integrazione tra microservizi: il controller delega al
 * {@link ProfiloClient}, che chiama il servizio remoto e ne normalizza la
 * risposta. Se il servizio non e raggiungibile l'errore diventa 502 (vedi
 * GlobalExceptionHandler); se l'utente non esiste, 404.
 */
@RestController
@RequestMapping("/api/profilo")
@Tag(name = "Profilo", description = "Dati di profilo da microservizio esterno")
public class ProfiloController {

    private final ProfiloClient profiloClient;

    public ProfiloController(ProfiloClient profiloClient) {
        this.profiloClient = profiloClient;
    }

    @GetMapping("/{username}")
    @Operation(summary = "Recupera i dati di profilo di un utente dal servizio esterno")
    public DatiProfilo dettaglio(@PathVariable String username) {
        return profiloClient.recuperaPerUsername(username)
                .orElseThrow(() -> new RisorsaNonTrovataException(
                        "Profilo non trovato per l'utente " + username));
    }
}
