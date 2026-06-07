package dev.protocollo.service;

import dev.protocollo.domain.Documento;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.messaging.ProtocollazioneProducer;
import dev.protocollo.repository.DocumentoRepository;
import dev.protocollo.security.UtenteAutenticato;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Logica applicativa per la gestione dei documenti.
 *
 * Concentra qui le regole di business (assegnazione del numero di protocollo,
 * controllo dei permessi, pubblicazione degli eventi Kafka) tenendole separate
 * dal livello web (i controller) e da quello di persistenza (i repository).
 */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final DocumentoRepository documentoRepository;
    private final ProtocollazioneProducer protocollazioneProducer;

    public DocumentoService(DocumentoRepository documentoRepository,
                            ProtocollazioneProducer protocollazioneProducer) {
        this.documentoRepository = documentoRepository;
        this.protocollazioneProducer = protocollazioneProducer;
    }

    /**
     * Restituisce una pagina di documenti.
     * Operazione di sola lettura: la transazione e marcata readOnly per
     * permettere a Hibernate alcune ottimizzazioni.
     */
    @Transactional(readOnly = true)
    public Page<Documento> elenca(Pageable pageable) {
        return documentoRepository.findAll(pageable);
    }

    /**
     * Recupera un singolo documento per id.
     *
     * @throws RisorsaNonTrovataException se il documento non esiste
     */
    @Transactional(readOnly = true)
    public Documento trova(Long id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException(
                        "Documento non trovato con id " + id));
    }

    /**
     * Crea un nuovo documento, gli assegna un numero di protocollo progressivo
     * e pubblica un evento di protocollazione su Kafka.
     *
     * @param titolo     titolo del documento
     * @param contenuto  corpo del documento
     * @param utente     utente autenticato che effettua l'operazione (diventa proprietario)
     */
    @Transactional
    public Documento crea(String titolo, String contenuto, UtenteAutenticato utente) {
        Documento documento = new Documento(titolo, contenuto, utente.getUsername());

        // Prima persistenza: serve per ottenere l'id generato dal database
        documento = documentoRepository.save(documento);

        // Assegno numero di protocollo e stato; essendo l'entita "managed",
        // la modifica viene salvata automaticamente al commit della transazione
        documento.setNumeroProtocollo(generaNumeroProtocollo(documento.getId()));
        documento.setStato(StatoDocumento.PROTOCOLLATO);

        log.info("Creato documento id={} numeroProtocollo={} da utente={}",
                documento.getId(), documento.getNumeroProtocollo(), utente.getUsername());

        pubblicaEvento(ProtocollazioneEvent.TipoOperazione.CREAZIONE, documento);
        return documento;
    }

    /**
     * Aggiorna titolo e contenuto di un documento esistente e pubblica un
     * evento di protocollazione.
     *
     * Il controllo dei permessi e qui: solo il proprietario del documento o un
     * amministratore possono modificarlo.
     *
     * @throws RisorsaNonTrovataException se il documento non esiste
     * @throws AccessDeniedException      se l'utente non ha i permessi
     */
    @Transactional
    public Documento aggiorna(Long id, String titolo, String contenuto, UtenteAutenticato utente) {
        Documento documento = trova(id);

        verificaPermessoModifica(documento, utente);

        documento.setTitolo(titolo);
        documento.setContenuto(contenuto);

        log.info("Aggiornato documento id={} da utente={}", id, utente.getUsername());

        pubblicaEvento(ProtocollazioneEvent.TipoOperazione.AGGIORNAMENTO, documento);
        return documento;
    }

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
     * Genera un numero di protocollo nel formato PRT-<anno>-<id a 6 cifre>.
     */
    private String generaNumeroProtocollo(Long id) {
        return String.format("PRT-%d-%06d", Year.now().getValue(), id);
    }

    private void pubblicaEvento(ProtocollazioneEvent.TipoOperazione tipo, Documento documento) {
        protocollazioneProducer.pubblica(ProtocollazioneEvent.di(
                tipo,
                documento.getId(),
                documento.getTitolo(),
                documento.getNumeroProtocollo(),
                documento.getProprietario()));
    }
}
