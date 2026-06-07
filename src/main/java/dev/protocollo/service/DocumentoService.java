package dev.protocollo.service;

import dev.protocollo.config.AccreditamentoProperties;
import dev.protocollo.config.CacheConfig;
import dev.protocollo.domain.Documento;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.domain.Utente;
import dev.protocollo.messaging.IndiceAggiornamentoEvent;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.messaging.outbox.OutboxService;
import dev.protocollo.pdf.DatiAccreditamento;
import dev.protocollo.pdf.DocumentoPdfService;
import dev.protocollo.repository.DocumentoRepository;
import dev.protocollo.repository.DocumentoSpecifications;
import dev.protocollo.repository.FiltroDocumenti;
import dev.protocollo.repository.UtenteRepository;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;

/**
 * Logica applicativa per la gestione dei documenti.
 *
 * Concentra qui le regole di business (assegnazione del numero di protocollo,
 * controllo dei permessi, generazione del PDF di accreditamento, pubblicazione
 * degli eventi tramite outbox) tenendole separate dal livello web (i controller)
 * e da quello di persistenza (i repository).
 */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final DocumentoRepository documentoRepository;
    private final UtenteRepository utenteRepository;
    private final OutboxService outboxService;
    private final DocumentStorage documentStorage;
    private final DocumentoPdfService pdfService;
    private final AccreditamentoProperties accreditamentoProperties;

    public DocumentoService(DocumentoRepository documentoRepository,
                            UtenteRepository utenteRepository,
                            OutboxService outboxService,
                            DocumentStorage documentStorage,
                            DocumentoPdfService pdfService,
                            AccreditamentoProperties accreditamentoProperties) {
        this.documentoRepository = documentoRepository;
        this.utenteRepository = utenteRepository;
        this.outboxService = outboxService;
        this.documentStorage = documentStorage;
        this.pdfService = pdfService;
        this.accreditamentoProperties = accreditamentoProperties;
    }

    /**
     * Restituisce una pagina di documenti applicando i filtri eventualmente
     * presenti. Operazione di sola lettura.
     */
    @Transactional(readOnly = true)
    public Page<Documento> elenca(FiltroDocumenti filtro, Pageable pageable) {
        return documentoRepository.findAll(DocumentoSpecifications.daFiltro(filtro), pageable);
    }

    /**
     * Recupera un singolo documento per id. Il risultato viene messo in cache:
     * letture successive dello stesso id non interrogano il database finche la
     * voce non scade o non viene invalidata da un aggiornamento.
     *
     * @throws RisorsaNonTrovataException se il documento non esiste
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#id")
    public Documento trova(Long id) {
        log.debug("Lettura documento {} dal database (cache miss)", id);
        return documentoRepository.findById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException(
                        "Documento non trovato con id " + id));
    }

    /**
     * Crea un nuovo documento, gli assegna un numero di protocollo, genera il
     * PDF di accreditamento e lo salva sullo storage, infine registra l'evento
     * di protocollazione nell'outbox.
     */
    @Transactional
    public Documento crea(String titolo, String contenuto, UtenteAutenticato utente) {
        Documento documento = new Documento(titolo, contenuto, utente.getUsername());

        // Prima persistenza: serve per ottenere l'id generato dal database
        documento = documentoRepository.save(documento);

        // Assegno numero di protocollo e stato (salvati al commit per dirty checking)
        documento.setNumeroProtocollo(generaNumeroProtocollo(documento.getId()));
        documento.setStato(StatoDocumento.PROTOCOLLATO);

        // Genero il PDF e lo carico sullo storage, memorizzandone il riferimento
        documento.setPdfRiferimento(generaESalvaPdf(documento));

        log.info("Creato documento id={} numeroProtocollo={} da utente={}",
                documento.getId(), documento.getNumeroProtocollo(), utente.getUsername());

        registraEvento(ProtocollazioneEvent.TipoOperazione.CREAZIONE, documento);
        return documento;
    }

    /**
     * Aggiorna titolo e contenuto di un documento esistente, rigenera il PDF e
     * registra un evento di protocollazione. Invalida la cache del documento.
     *
     * Solo il proprietario del documento o un amministratore possono modificarlo.
     *
     * @throws RisorsaNonTrovataException se il documento non esiste
     * @throws AccessDeniedException      se l'utente non ha i permessi
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#id")
    public Documento aggiorna(Long id, String titolo, String contenuto, UtenteAutenticato utente) {
        Documento documento = trova(id);

        verificaPermessoModifica(documento, utente);

        documento.setTitolo(titolo);
        documento.setContenuto(contenuto);

        // Rigenero il PDF mantenendo la stessa chiave (sovrascrittura)
        documento.setPdfRiferimento(generaESalvaPdf(documento));

        log.info("Aggiornato documento id={} da utente={}", id, utente.getUsername());

        registraEvento(ProtocollazioneEvent.TipoOperazione.AGGIORNAMENTO, documento);
        return documento;
    }

    /**
     * Recupera i byte del PDF associato al documento, leggendoli dallo storage.
     *
     * @throws RisorsaNonTrovataException se il documento o il suo PDF non esistono
     */
    @Transactional(readOnly = true)
    public byte[] scaricaPdf(Long id) {
        Documento documento = trova(id);
        if (documento.getPdfRiferimento() == null) {
            throw new RisorsaNonTrovataException("PDF non disponibile per il documento " + id);
        }
        return documentStorage.leggi(documento.getPdfRiferimento());
    }

    /**
     * Applica al documento locale un aggiornamento ricevuto dall'indice esterno
     * (tramite il consumer Kafka): allinea lo stato e marca la risorsa come
     * indicizzata. Invalida la cache del documento interessato.
     *
     * Non lancia eccezioni se il documento non esiste: si limita a loggare, per
     * non bloccare il consumo del messaggio.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#evento.idDocumento()")
    public void applicaAggiornamentoIndice(IndiceAggiornamentoEvent evento) {
        documentoRepository.findById(evento.idDocumento()).ifPresentOrElse(documento -> {
            if (evento.nuovoStato() != null) {
                documento.setStato(evento.nuovoStato());
            }
            documento.setIndicizzato(true);
            documento.setDataIndicizzazione(Instant.now());
            log.info("Documento {} allineato dall'indice '{}' (stato={})",
                    documento.getId(), evento.origine(), documento.getStato());
        }, () -> log.warn("Aggiornamento indice ignorato: documento {} inesistente",
                evento.idDocumento()));
    }

    // --- Metodi di supporto --------------------------------------------------

    /**
     * Un utente puo modificare un documento solo se ne e il proprietario o se
     * possiede il ruolo di amministratore.
     */
    private void verificaPermessoModifica(Documento documento, UtenteAutenticato utente) {
        boolean eProprietario = documento.getProprietario().equals(utente.getUsername());
        if (!eProprietario && !utente.isAmministratore()) {
            throw new AccessDeniedException(
                    "Non hai i permessi per modificare il documento " + documento.getId());
        }
    }

    /**
     * Genera il PDF del documento (con i dati del proprietario e i servizi di
     * accreditamento configurati) e lo salva sullo storage, restituendo il
     * riferimento (chiave) con cui ritrovarlo.
     */
    private String generaESalvaPdf(Documento documento) {
        // Recupero i dati del proprietario; se mancante uso lo username come fallback
        Utente proprietario = utenteRepository.findByUsername(documento.getProprietario()).orElse(null);
        String nomeCompleto = proprietario != null ? proprietario.getNomeCompleto() : documento.getProprietario();
        String email = proprietario != null ? proprietario.getEmail() : null;

        DatiAccreditamento dati = new DatiAccreditamento(
                documento.getTitolo(),
                documento.getNumeroProtocollo(),
                nomeCompleto,
                email,
                documento.getProprietario(),
                documento.getDataCreazione(),
                documento.getContenuto(),
                accreditamentoProperties.serviziSicuri());

        byte[] pdf = pdfService.genera(dati);
        String chiave = "documenti/" + documento.getNumeroProtocollo() + ".pdf";
        return documentStorage.salva(chiave, pdf, CONTENT_TYPE_PDF);
    }

    /**
     * Genera un numero di protocollo nel formato PRT-<anno>-<id a 6 cifre>.
     */
    private String generaNumeroProtocollo(Long id) {
        return String.format("PRT-%d-%06d", Year.now().getValue(), id);
    }

    /**
     * Registra l'evento nell'outbox (stessa transazione del salvataggio): sara
     * il publisher schedulato a inviarlo effettivamente su Kafka.
     */
    private void registraEvento(ProtocollazioneEvent.TipoOperazione tipo, Documento documento) {
        outboxService.registraProtocollazione(ProtocollazioneEvent.di(
                tipo,
                documento.getId(),
                documento.getTitolo(),
                documento.getNumeroProtocollo(),
                documento.getProprietario(),
                documento.getPdfRiferimento()));
    }
}
