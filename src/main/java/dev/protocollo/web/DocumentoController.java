package dev.protocollo.web;

import dev.protocollo.domain.Documento;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.repository.FiltroDocumenti;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.service.DocumentoService;
import dev.protocollo.web.dto.DocumentoRequest;
import dev.protocollo.web.dto.DocumentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Controller REST per la gestione dei documenti.
 *
 * Espone:
 *  - GET  /api/documenti          elenco paginato e filtrabile
 *  - GET  /api/documenti/{id}     dettaglio singolo (con cache)
 *  - GET  /api/documenti/{id}/pdf download del PDF dallo storage
 *  - POST /api/documenti          creazione (ruolo USER o ADMIN)
 *  - PUT  /api/documenti/{id}     aggiornamento (solo proprietario o ADMIN)
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

    /**
     * Elenco paginato con filtri facoltativi. La paginazione e l'ordinamento
     * arrivano dal parametro {@link Pageable} (es. ?page=0&size=10&sort=dataCreazione,desc).
     */
    @GetMapping
    @Operation(summary = "Elenca i documenti in modo paginato, con filtri facoltativi")
    public Page<DocumentoResponse> elenca(
            @RequestParam(required = false) StatoDocumento stato,
            @RequestParam(required = false) String proprietario,
            @RequestParam(required = false) String testo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant creatoDa,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant creatoA,
            Pageable pageable) {

        FiltroDocumenti filtro = new FiltroDocumenti(stato, proprietario, testo, creatoDa, creatoA);
        return documentoService.elenca(filtro, pageable).map(DocumentoResponse::da);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Recupera un documento per id")
    public DocumentoResponse dettaglio(@PathVariable Long id) {
        Documento documento = documentoService.trova(id);
        return DocumentoResponse.da(documento);
    }

    /**
     * Scarica il PDF del documento, letto dallo storage (locale o S3 a seconda
     * del profilo attivo).
     */
    @GetMapping("/{id}/pdf")
    @Operation(summary = "Scarica il PDF del documento")
    public ResponseEntity<byte[]> scaricaPdf(@PathVariable Long id) {
        byte[] pdf = documentoService.scaricaPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"documento-" + id + ".pdf\"")
                .body(pdf);
    }

    /**
     * Creazione: richiede il ruolo USER o ADMIN. Il controllo a grana grossa e
     * fatto qui con {@code @PreAuthorize}; il proprietario viene impostato dal
     * service a partire dall'utente autenticato.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Crea un nuovo documento, genera il PDF e pubblica la protocollazione su Kafka")
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
    @Operation(summary = "Aggiorna un documento esistente, rigenera il PDF e pubblica l'evento su Kafka")
    public DocumentoResponse aggiorna(@PathVariable Long id,
                                      @Valid @RequestBody DocumentoRequest richiesta,
                                      @AuthenticationPrincipal UtenteAutenticato utente) {
        Documento documento = documentoService.aggiorna(
                id, richiesta.titolo(), richiesta.contenuto(), utente);
        return DocumentoResponse.da(documento);
    }
}
