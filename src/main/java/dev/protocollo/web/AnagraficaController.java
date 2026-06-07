package dev.protocollo.web;

import dev.protocollo.client.AnagraficaClient;
import dev.protocollo.client.DatiAnagrafici;
import dev.protocollo.service.RisorsaNonTrovataException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espone i dati anagrafici recuperati da un microservizio esterno.
 *
 * Mostra l'integrazione tra microservizi: il controller delega al
 * {@link AnagraficaClient}, che chiama il servizio remoto e ne normalizza la
 * risposta. Se il servizio non e raggiungibile l'errore diventa 502 (vedi
 * GlobalExceptionHandler); se l'utente non esiste, 404.
 */
@RestController
@RequestMapping("/api/anagrafica")
@Tag(name = "Anagrafica", description = "Dati anagrafici da microservizio esterno")
public class AnagraficaController {

    private final AnagraficaClient anagraficaClient;

    public AnagraficaController(AnagraficaClient anagraficaClient) {
        this.anagraficaClient = anagraficaClient;
    }

    @GetMapping("/{username}")
    @Operation(summary = "Recupera i dati anagrafici di un utente dal servizio esterno")
    public DatiAnagrafici dettaglio(@PathVariable String username) {
        return anagraficaClient.recuperaPerUsername(username)
                .orElseThrow(() -> new RisorsaNonTrovataException(
                        "Anagrafica non trovata per l'utente " + username));
    }
}
