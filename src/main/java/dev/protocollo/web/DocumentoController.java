package dev.protocollo.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import dev.protocollo.domain.Documento;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.service.DocumentoService;
import dev.protocollo.web.dto.DocumentoRequest;
import dev.protocollo.web.dto.DocumentoResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST per la gestione dei documenti.
 *
 * Espone tre operazioni:
 *  - GET  /api/documenti        elenco paginato (chiunque sia autenticato)
 *  - GET  /api/documenti/{id}   dettaglio singolo
 *  - POST /api/documenti        creazione (ruolo USER o ADMIN)
 *  - PUT  /api/documenti/{id}   aggiornamento (solo proprietario o ADMIN)
 *
 * L'utente autenticato viene iniettato con {@code @AuthenticationPrincipal}:
 * e l'oggetto messo nel SecurityContext dal filtro JWT.
 */
@RestController
@RequestMapping("/api/documenti")
@Tag(name = "Documenti", description = "Creazione, consultazione e aggiornamento dei documenti")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping
    @Operation(summary = "Elenca i documenti in modo paginato")
    public Page<DocumentoResponse> elenca(Pageable pageable) {
        return documentoService.elenca(pageable).map(DocumentoResponse::da);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recupera un documento per id")
    public DocumentoResponse dettaglio(@PathVariable Long id) {
        Documento documento = documentoService.trova(id);
        return DocumentoResponse.da(documento);
    }

    /**
     * Creazione: richiede il ruolo USER o ADMIN. Il controllo a grana grossa e
     * fatto qui con {@code @PreAuthorize}; il proprietario viene impostato dal
     * service a partire dall'utente autenticato.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Crea un nuovo documento e ne pubblica la protocollazione su Kafka")
    public DocumentoResponse crea(@Valid @RequestBody DocumentoRequest richiesta,
                                  @AuthenticationPrincipal UtenteAutenticato utente) {
        Documento documento = documentoService.crea(
                richiesta.titolo(), richiesta.contenuto(), utente);
        return DocumentoResponse.da(documento);
    }

    /**
     * Aggiornamento: l'autorizzazione fine (proprietario o amministratore) e
     * verificata dentro il service, che conosce il proprietario del documento.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Aggiorna un documento esistente e pubblica l'evento su Kafka")
    public DocumentoResponse aggiorna(@PathVariable Long id,
                                      @Valid @RequestBody DocumentoRequest richiesta,
                                      @AuthenticationPrincipal UtenteAutenticato utente) {
        Documento documento = documentoService.aggiorna(
                id, richiesta.titolo(), richiesta.contenuto(), utente);
        return DocumentoResponse.da(documento);
    }
}
