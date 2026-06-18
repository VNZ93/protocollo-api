package dev.protocollo.service;

import dev.protocollo.config.AccreditamentoProperties;
import dev.protocollo.domain.Documento;
import dev.protocollo.domain.Ruolo;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.domain.Utente;
import dev.protocollo.messaging.IndiceAggiornamentoEvent;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.messaging.RichiestaProtocollazioneEvent;
import dev.protocollo.messaging.outbox.OutboxService;
import dev.protocollo.pdf.DatiAccreditamento;
import dev.protocollo.pdf.DocumentoPdfService;
import dev.protocollo.repository.DocumentoRepository;
import dev.protocollo.repository.UtenteRepository;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.storage.DocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitari della logica di business in {@link DocumentoService}.
 *
 * Le dipendenze (repository, outbox, storage e generatore PDF) sono sostituite
 * da mock Mockito, cosi da testare solo le regole del service in isolamento e
 * senza database ne I/O reale. Le proprieta di accreditamento sono un oggetto
 * reale, costruito a mano.
 */
@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {

    private static final long RITARDO_PROTOCOLLAZIONE_SECONDI = 60L;

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private UtenteRepository utenteRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private DocumentoPdfService pdfService;

    private DocumentoService documentoService;

    @BeforeEach
    void setUp() {
        documentoService = new DocumentoService(
                documentoRepository,
                utenteRepository,
                outboxService,
                documentStorage,
                pdfService,
                new AccreditamentoProperties(List.of("Gestione Documentale")),
                RITARDO_PROTOCOLLAZIONE_SECONDI);
    }

    // --- Helper ---------------------------------------------------------------

    private UtenteAutenticato utente(String username, Ruolo... ruoli) {
        Utente u = new Utente(username, "hash", "Nome " + username, Set.of(ruoli));
        return new UtenteAutenticato(u);
    }

    private Documento documentoEsistente(Long id, String proprietario, StatoDocumento stato) {
        Documento documento = new Documento("Titolo originale", "Contenuto", proprietario);
        ReflectionTestUtils.setField(documento, "id", id);
        documento.setStato(stato);
        return documento;
    }

    /** Stub comuni alla protocollazione: generazione e salvataggio del PDF. */
    private void stubPdf() {
        when(pdfService.genera(any(DatiAccreditamento.class))).thenReturn(new byte[]{1, 2, 3});
        when(documentStorage.salva(any(), any(), any())).thenReturn("documenti/test.pdf");
    }

    // --- Creazione ------------------------------------------------------------

    @Test
    void laCreazioneProduceUnaBozzaSenzaProtocolloNePdfERegistraEventoNellOutbox() {
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocazione -> {
            Documento daSalvare = invocazione.getArgument(0);
            ReflectionTestUtils.setField(daSalvare, "id", 42L);
            return daSalvare;
        });

        Documento risultato = documentoService.crea(
                "Nuova determina", "Testo della determina", utente("mrossi", Ruolo.USER));

        assertThat(risultato.getId()).isEqualTo(42L);
        assertThat(risultato.getProprietario()).isEqualTo("mrossi");
        assertThat(risultato.getStato()).isEqualTo(StatoDocumento.BOZZA);
        assertThat(risultato.getNumeroProtocollo()).isNull();
        assertThat(risultato.getPdfRiferimento()).isNull();

        ArgumentCaptor<ProtocollazioneEvent> captor =
                ArgumentCaptor.forClass(ProtocollazioneEvent.class);
        verify(outboxService).registraProtocollazione(captor.capture());
        assertThat(captor.getValue().operazione())
                .isEqualTo(ProtocollazioneEvent.TipoOperazione.CREAZIONE);
    }

    @Test
    void unAmministratoreNonPuoCreareDocumenti() {
        assertThatThrownBy(() -> documentoService.crea(
                "Titolo", "Contenuto", utente("admin", Ruolo.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);

        verify(documentoRepository, never()).save(any());
        verify(outboxService, never()).registraProtocollazione(any());
    }

    // --- Aggiornamento --------------------------------------------------------

    @Test
    void ilProprietarioPuoAggiornareUnaPropriaBozza() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi", StatoDocumento.BOZZA)));

        Documento risultato = documentoService.aggiorna(
                1L, "Titolo aggiornato", "Nuovo contenuto", utente("mrossi", Ruolo.USER));

        assertThat(risultato.getTitolo()).isEqualTo("Titolo aggiornato");
        verify(outboxService).registraProtocollazione(any(ProtocollazioneEvent.class));
    }

    @Test
    void unAmministratorePuoAggiornareBozzeAltrui() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi", StatoDocumento.BOZZA)));

        Documento risultato = documentoService.aggiorna(
                1L, "Corretto da admin", "Contenuto", utente("admin", Ruolo.ADMIN));

        assertThat(risultato.getTitolo()).isEqualTo("Corretto da admin");
    }

    @Test
    void unUtenteNonProprietarioNonPuoAggiornareEnonRegistraEventi() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi", StatoDocumento.BOZZA)));

        assertThatThrownBy(() -> documentoService.aggiorna(
                1L, "Tentativo", "Contenuto", utente("altro", Ruolo.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(outboxService, never()).registraProtocollazione(any());
    }

    @Test
    void aggiornareUnDocumentoInesistenteLanciaEccezione() {
        when(documentoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentoService.aggiorna(
                99L, "x", "y", utente("mrossi", Ruolo.USER)))
                .isInstanceOf(RisorsaNonTrovataException.class);
    }

    @Test
    void aggiornareUnDocumentoNonPiuInBozzaLanciaTransizioneNonValida() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi", StatoDocumento.PROTOCOLLATO)));

        assertThatThrownBy(() -> documentoService.aggiorna(
                1L, "Tentativo", "Contenuto", utente("mrossi", Ruolo.USER)))
                .isInstanceOf(TransizioneStatoNonValidaException.class);

        verify(outboxService, never()).registraProtocollazione(any());
    }

    // --- Approvazione -----------------------------------------------------------

    @Test
    void unAmministratorePuoApprovareUnaBozza() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi", StatoDocumento.BOZZA)));

        Documento risultato = documentoService.approva(1L, utente("admin", Ruolo.ADMIN));

        assertThat(risultato.getStato()).isEqualTo(StatoDocumento.APPROVATA);
        assertThat(risultato.getDataApprovazione()).isNotNull();

        ArgumentCaptor<ProtocollazioneEvent> captor =
                ArgumentCaptor.forClass(ProtocollazioneEvent.class);
        verify(outboxService).registraProtocollazione(captor.capture());
        assertThat(captor.getValue().operazione())
                .isEqualTo(ProtocollazioneEvent.TipoOperazione.APPROVAZIONE);
    }

    @Test
    void unNonAmministratoreNonPuoApprovare() {
        assertThatThrownBy(() -> documentoService.approva(1L, utente("mrossi", Ruolo.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(documentoRepository, never()).findById(any());
    }

    @Test
    void nonSiPuoApprovareUnDocumentoNonInBozza() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi", StatoDocumento.APPROVATA)));

        assertThatThrownBy(() -> documentoService.approva(1L, utente("admin", Ruolo.ADMIN)))
                .isInstanceOf(TransizioneStatoNonValidaException.class);
    }

    // --- Scansione per la protocollazione automatica -----------------------------

    @Test
    void unDocumentoApprovatoScadutoVieneAccodatoPerLaProtocollazione() {
        Documento documento = documentoEsistente(1L, "mrossi", StatoDocumento.APPROVATA);
        when(documentoRepository.findByStatoAndProtocollazioneInCodaFalseAndDataApprovazioneLessThanEqual(
                eq(StatoDocumento.APPROVATA), any(Instant.class)))
                .thenReturn(List.of(documento));

        documentoService.avviaProtocollazioneScadute();

        assertThat(documento.isProtocollazioneInCoda()).isTrue();

        ArgumentCaptor<RichiestaProtocollazioneEvent> captor =
                ArgumentCaptor.forClass(RichiestaProtocollazioneEvent.class);
        verify(outboxService).registraRichiestaProtocollazione(captor.capture());
        assertThat(captor.getValue().idDocumento()).isEqualTo(1L);
    }

    @Test
    void unErroreSuUnDocumentoNonBloccaLaScansioneDegliAltri() {
        Documento primo = documentoEsistente(1L, "mrossi", StatoDocumento.APPROVATA);
        Documento secondo = documentoEsistente(2L, "mbianchi", StatoDocumento.APPROVATA);
        when(documentoRepository.findByStatoAndProtocollazioneInCodaFalseAndDataApprovazioneLessThanEqual(
                eq(StatoDocumento.APPROVATA), any(Instant.class)))
                .thenReturn(List.of(primo, secondo));
        doThrow(new RuntimeException("outbox non disponibile"))
                .when(outboxService).registraRichiestaProtocollazione(
                        argThat(evento -> evento.idDocumento().equals(1L)));

        assertThatCode(() -> documentoService.avviaProtocollazioneScadute())
                .doesNotThrowAnyException();

        assertThat(secondo.isProtocollazioneInCoda()).isTrue();
        verify(outboxService).registraRichiestaProtocollazione(
                argThat(evento -> evento.idDocumento().equals(2L)));
    }

    // --- Esecuzione della protocollazione automatica -----------------------------

    @Test
    void laProtocollazioneAutomaticaAssegnaProtocolloPdfECambiaStato() {
        stubPdf();
        Documento documento = documentoEsistente(1L, "mrossi", StatoDocumento.APPROVATA);
        ReflectionTestUtils.setField(documento, "protocollazioneInCoda", true);
        when(documentoRepository.findById(1L)).thenReturn(Optional.of(documento));

        documentoService.eseguiProtocollazione(1L);

        assertThat(documento.getStato()).isEqualTo(StatoDocumento.PROTOCOLLATO);
        assertThat(documento.getNumeroProtocollo()).matches("PRT-\\d{4}-000001");
        assertThat(documento.getPdfRiferimento()).isEqualTo("documenti/test.pdf");
        assertThat(documento.isProtocollazioneInCoda()).isFalse();

        ArgumentCaptor<ProtocollazioneEvent> captor =
                ArgumentCaptor.forClass(ProtocollazioneEvent.class);
        verify(outboxService).registraProtocollazione(captor.capture());
        assertThat(captor.getValue().operazione())
                .isEqualTo(ProtocollazioneEvent.TipoOperazione.PROTOCOLLAZIONE);
    }

    @Test
    void laProtocollazioneAutomaticaSuUnDocumentoGiaProtocollatoNonFaNulla() {
        Documento documento = documentoEsistente(1L, "mrossi", StatoDocumento.PROTOCOLLATO);
        when(documentoRepository.findById(1L)).thenReturn(Optional.of(documento));

        documentoService.eseguiProtocollazione(1L);

        verify(outboxService, never()).registraProtocollazione(any());
        verify(pdfService, never()).genera(any());
    }

    @Test
    void laProtocollazioneAutomaticaSuUnDocumentoInesistenteNonFaNulla() {
        when(documentoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatCode(() -> documentoService.eseguiProtocollazione(99L))
                .doesNotThrowAnyException();

        verify(outboxService, never()).registraProtocollazione(any());
    }

    // --- Archiviazione ------------------------------------------------------------

    @Test
    void ilProprietarioPuoArchiviareUnDocumentoInQualsiasiStato() {
        Documento documento = documentoEsistente(1L, "mrossi", StatoDocumento.PROTOCOLLATO);
        when(documentoRepository.findById(1L)).thenReturn(Optional.of(documento));

        Documento risultato = documentoService.archivia(1L, utente("mrossi", Ruolo.USER));

        assertThat(risultato.isArchiviato()).isTrue();
        assertThat(risultato.getStato()).isEqualTo(StatoDocumento.PROTOCOLLATO);
        verify(outboxService, never()).registraProtocollazione(any());
    }

    @Test
    void siPuoRimuovereIlTagDiArchiviazione() {
        Documento documento = documentoEsistente(1L, "mrossi", StatoDocumento.PROTOCOLLATO);
        documento.setArchiviato(true);
        when(documentoRepository.findById(1L)).thenReturn(Optional.of(documento));

        Documento risultato = documentoService.disarchivia(1L, utente("mrossi", Ruolo.USER));

        assertThat(risultato.isArchiviato()).isFalse();
    }

    @Test
    void unUtenteNonProprietarioNonPuoArchiviare() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi", StatoDocumento.BOZZA)));

        assertThatThrownBy(() -> documentoService.archivia(1L, utente("altro", Ruolo.USER)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- Aggiornamento dall'indice esterno ------------------------------------

    @Test
    void unAggiornamentoDallIndiceAllineaStatoEMarcaIndicizzato() {
        Documento documento = documentoEsistente(5L, "mrossi", StatoDocumento.BOZZA);
        when(documentoRepository.findById(5L)).thenReturn(Optional.of(documento));

        documentoService.applicaAggiornamentoIndice(
                new IndiceAggiornamentoEvent(5L, StatoDocumento.APPROVATA, "motore-indicizzazione"));

        assertThat(documento.getStato()).isEqualTo(StatoDocumento.APPROVATA);
        assertThat(documento.isIndicizzato()).isTrue();
        assertThat(documento.getDataIndicizzazione()).isNotNull();
    }

    @Test
    void unAggiornamentoDallIndicePerDocumentoInesistenteNonLanciaEccezioni() {
        when(documentoRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatCode(() -> documentoService.applicaAggiornamentoIndice(
                new IndiceAggiornamentoEvent(7L, StatoDocumento.APPROVATA, "motore")))
                .doesNotThrowAnyException();
    }
}
