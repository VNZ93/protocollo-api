package dev.protocollo.service;

import dev.protocollo.config.AccreditamentoProperties;
import dev.protocollo.config.CacheConfig;
import dev.protocollo.domain.Documento;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.domain.Utente;
import dev.protocollo.messaging.IndiceAggiornamentoEvent;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.messaging.RichiestaProtocollazioneEvent;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;

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
    private final long ritardoProtocollazioneSecondi;

    public DocumentoService(DocumentoRepository documentoRepository,
                            UtenteRepository utenteRepository,
                            OutboxService outboxService,
                            DocumentStorage documentStorage,
                            DocumentoPdfService pdfService,
                            AccreditamentoProperties accreditamentoProperties,
                            @Value("${app.protocollazione.ritardo-secondi:60}") long ritardoProtocollazioneSecondi) {
        this.documentoRepository = documentoRepository;
        this.utenteRepository = utenteRepository;
        this.outboxService = outboxService;
        this.documentStorage = documentStorage;
        this.pdfService = pdfService;
        this.accreditamentoProperties = accreditamentoProperties;
        this.ritardoProtocollazioneSecondi = ritardoProtocollazioneSecondi;
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
     * Crea un nuovo documento in stato BOZZA. Solo un utente non amministratore
     * puo creare documenti: l'approvazione (e la successiva protocollazione
     * automatica) sono compiti dell'amministratore e del sistema, non di chi crea.
     *
     * Numero di protocollo e PDF non sono ancora assegnati: arriveranno solo
     * dopo l'approvazione, quando il job di scansione e il consumer Kafka
     * eseguiranno davvero la protocollazione (vedi {@link #eseguiProtocollazione}).
     *
     * @throws AccessDeniedException se l'utente e un amministratore
     */
    @Transactional
    public Documento crea(String titolo, String contenuto, UtenteAutenticato utente) {
        if (utente.isAmministratore()) {
            throw new AccessDeniedException("Solo un utente non amministratore puo creare un documento");
        }

        Documento documento = new Documento(titolo, contenuto, utente.getUsername());
        documento = documentoRepository.save(documento);

        log.info("Creato documento id={} in stato BOZZA da utente={}",
                documento.getId(), utente.getUsername());

        registraEvento(ProtocollazioneEvent.TipoOperazione.CREAZIONE, documento);
        return documento;
    }

    /**
     * Aggiorna titolo e contenuto di un documento esistente e registra un
     * evento di protocollazione. Invalida la cache del documento.
     *
     * Solo il proprietario del documento o un amministratore possono modificarlo,
     * e solo finche il documento e ancora in BOZZA: una volta approvato (o
     * protocollato) il contenuto si considera definitivo.
     *
     * @throws RisorsaNonTrovataException        se il documento non esiste
     * @throws AccessDeniedException             se l'utente non ha i permessi
     * @throws TransizioneStatoNonValidaException se il documento non e piu in BOZZA
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#id")
    public Documento aggiorna(Long id, String titolo, String contenuto, UtenteAutenticato utente) {
        Documento documento = trova(id);

        verificaPermessoModifica(documento, utente);

        if (documento.getStato() != StatoDocumento.BOZZA) {
            throw new TransizioneStatoNonValidaException(
                    "Il documento non e piu una bozza: non puo essere modificato");
        }

        documento.setTitolo(titolo);
        documento.setContenuto(contenuto);

        log.info("Aggiornato documento id={} da utente={}", id, utente.getUsername());

        registraEvento(ProtocollazioneEvent.TipoOperazione.AGGIORNAMENTO, documento);
        return documento;
    }

    /**
     * Approva un documento in BOZZA: lo sposta in APPROVATA e registra il
     * momento dell'approvazione, da cui parte il conto alla rovescia per la
     * protocollazione automatica (vedi {@link #avviaProtocollazioneScadute}).
     *
     * Solo un amministratore puo approvare.
     *
     * @throws RisorsaNonTrovataException         se il documento non esiste
     * @throws AccessDeniedException              se l'utente non e amministratore
     * @throws TransizioneStatoNonValidaException se il documento non e in BOZZA
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#id")
    public Documento approva(Long id, UtenteAutenticato utente) {
        if (!utente.isAmministratore()) {
            throw new AccessDeniedException("Solo un amministratore puo approvare un documento");
        }

        Documento documento = trova(id);
        if (documento.getStato() != StatoDocumento.BOZZA) {
            throw new TransizioneStatoNonValidaException(
                    "Solo un documento in bozza puo essere approvato");
        }

        documento.setStato(StatoDocumento.APPROVATA);
        documento.setDataApprovazione(Instant.now());

        log.info("Documento {} approvato da {}", id, utente.getUsername());

        registraEvento(ProtocollazioneEvent.TipoOperazione.APPROVAZIONE, documento);
        return documento;
    }

    /**
     * Job di scansione: cerca i documenti APPROVATA il cui ritardo configurato
     * e scaduto e li accoda per la protocollazione automatica, scrivendo una
     * richiesta nell'outbox (pubblicata poi su Kafka da {@code OutboxPublisher}).
     *
     * Non esegue la protocollazione qui: e solo il "trigger" che passa la mano
     * a {@link #eseguiProtocollazione}, chiamato dal consumer Kafka dedicato.
     * Il flag {@code protocollazioneInCoda} evita di accodare lo stesso
     * documento piu volte mentre il consumer non l'ha ancora elaborato.
     */
    @Scheduled(fixedDelayString = "${app.protocollazione.intervallo-controllo-ms:10000}")
    @Transactional
    public void avviaProtocollazioneScadute() {
        Instant scadenza = Instant.now().minusSeconds(ritardoProtocollazioneSecondi);
        List<Documento> daAccodare = documentoRepository
                .findByStatoAndProtocollazioneInCodaFalseAndDataApprovazioneLessThanEqual(
                        StatoDocumento.APPROVATA, scadenza);

        for (Documento documento : daAccodare) {
            try {
                documento.setProtocollazioneInCoda(true);
                outboxService.registraRichiestaProtocollazione(
                        RichiestaProtocollazioneEvent.di(documento.getId()));
                log.info("Documento {} accodato per la protocollazione automatica", documento.getId());
            } catch (Exception e) {
                log.error("Accodamento protocollazione fallito per il documento {}: {}",
                        documento.getId(), e.getMessage());
            }
        }
    }

    /**
     * Esegue davvero la protocollazione automatica: assegna numero di
     * protocollo, genera e salva il PDF, sposta il documento in PROTOCOLLATO.
     * Chiamato dal consumer Kafka della coda di lavoro, non da HTTP.
     *
     * Idempotente: Kafka garantisce solo consegna "at-least-once", quindi se il
     * documento non esiste piu o non e piu APPROVATA (gia protocollato, o
     * messaggio ricevuto due volte) il metodo non fa nulla.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#idDocumento")
    public void eseguiProtocollazione(Long idDocumento) {
        Documento documento = documentoRepository.findById(idDocumento).orElse(null);
        if (documento == null || documento.getStato() != StatoDocumento.APPROVATA) {
            log.info("Protocollazione automatica ignorata per il documento {} (stato attuale: {})",
                    idDocumento, documento != null ? documento.getStato() : "inesistente");
            return;
        }

        documento.setNumeroProtocollo(generaNumeroProtocollo(documento.getId()));
        documento.setPdfRiferimento(generaESalvaPdf(documento));
        documento.setStato(StatoDocumento.PROTOCOLLATO);
        documento.setProtocollazioneInCoda(false);

        log.info("Documento {} protocollato automaticamente, numeroProtocollo={}",
                documento.getId(), documento.getNumeroProtocollo());

        registraEvento(ProtocollazioneEvent.TipoOperazione.PROTOCOLLAZIONE, documento);
    }

    /**
     * Marca un documento come archiviato. L'archiviazione e un tag indipendente
     * dallo stato: un documento archiviato resta nel suo stato del ciclo di vita,
     * ma finisce nel tab "Archivio" del frontend a prescindere da quale sia.
     *
     * Stessi permessi di {@link #aggiorna}: proprietario o amministratore.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#id")
    public Documento archivia(Long id, UtenteAutenticato utente) {
        Documento documento = trova(id);
        verificaPermessoModifica(documento, utente);
        documento.setArchiviato(true);
        log.info("Documento {} archiviato da {}", id, utente.getUsername());
        return documento;
    }

    /** Rimuove il tag di archiviazione. Stessi permessi di {@link #archivia}. */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CACHE_DOCUMENTI, key = "#id")
    public Documento disarchivia(Long id, UtenteAutenticato utente) {
        Documento documento = trova(id);
        verificaPermessoModifica(documento, utente);
        documento.setArchiviato(false);
        log.info("Documento {} rimosso dall'archivio da {}", id, utente.getUsername());
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
