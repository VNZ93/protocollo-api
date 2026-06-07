package dev.protocollo.service;

import dev.protocollo.domain.Documento;
import dev.protocollo.domain.Ruolo;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.domain.Utente;
import dev.protocollo.messaging.IndiceAggiornamentoEvent;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.messaging.ProtocollazioneProducer;
import dev.protocollo.pdf.DocumentoPdfService;
import dev.protocollo.repository.DocumentoRepository;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.storage.DocumentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitari della logica di business in {@link DocumentoService}.
 *
 * Le dipendenze (repository, producer Kafka, storage e generatore PDF) sono
 * sostituite da mock Mockito, cosi da testare solo le regole del service in
 * isolamento e senza database ne I/O reale.
 */
@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private ProtocollazioneProducer protocollazioneProducer;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private DocumentoPdfService pdfService;

    @InjectMocks
    private DocumentoService documentoService;

    // --- Helper ---------------------------------------------------------------

    private UtenteAutenticato utente(String username, Ruolo... ruoli) {
        Utente u = new Utente(username, "hash", "Nome " + username, Set.of(ruoli));
        return new UtenteAutenticato(u);
    }

    private Documento documentoEsistente(Long id, String proprietario) {
        Documento documento = new Documento("Titolo originale", "Contenuto", proprietario);
        ReflectionTestUtils.setField(documento, "id", id);
        documento.setStato(StatoDocumento.PROTOCOLLATO);
        return documento;
    }

    /** Stub comuni a creazione e aggiornamento: generazione e salvataggio del PDF. */
    private void stubPdf() {
        when(pdfService.genera(any(Documento.class))).thenReturn(new byte[]{1, 2, 3});
        when(documentStorage.salva(any(), any(), any())).thenReturn("documenti/test.pdf");
    }

    // --- Creazione ------------------------------------------------------------

    @Test
    void laCreazioneAssegnaProtocolloGeneraPdfEPubblicaEvento() {
        stubPdf();
        // Il save simula il DB assegnando l'id all'entita e restituendola
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocazione -> {
            Documento daSalvare = invocazione.getArgument(0);
            ReflectionTestUtils.setField(daSalvare, "id", 42L);
            return daSalvare;
        });

        Documento risultato = documentoService.crea(
                "Nuova determina", "Testo della determina", utente("mrossi", Ruolo.USER));

        assertThat(risultato.getId()).isEqualTo(42L);
        assertThat(risultato.getProprietario()).isEqualTo("mrossi");
        assertThat(risultato.getStato()).isEqualTo(StatoDocumento.PROTOCOLLATO);
        assertThat(risultato.getNumeroProtocollo()).matches("PRT-\\d{4}-000042");
        assertThat(risultato.getPdfRiferimento()).isEqualTo("documenti/test.pdf");

        // Verifico che sia stato pubblicato un evento di tipo CREAZIONE
        ArgumentCaptor<ProtocollazioneEvent> captor =
                ArgumentCaptor.forClass(ProtocollazioneEvent.class);
        verify(protocollazioneProducer).pubblica(captor.capture());
        assertThat(captor.getValue().operazione())
                .isEqualTo(ProtocollazioneEvent.TipoOperazione.CREAZIONE);
    }

    // --- Aggiornamento --------------------------------------------------------

    @Test
    void ilProprietarioPuoAggiornareIlProprioDocumento() {
        stubPdf();
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi")));

        Documento risultato = documentoService.aggiorna(
                1L, "Titolo aggiornato", "Nuovo contenuto", utente("mrossi", Ruolo.USER));

        assertThat(risultato.getTitolo()).isEqualTo("Titolo aggiornato");
        verify(protocollazioneProducer).pubblica(any(ProtocollazioneEvent.class));
    }

    @Test
    void unAmministratorePuoAggiornareDocumentiAltrui() {
        stubPdf();
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi")));

        Documento risultato = documentoService.aggiorna(
                1L, "Corretto da admin", "Contenuto", utente("admin", Ruolo.ADMIN));

        assertThat(risultato.getTitolo()).isEqualTo("Corretto da admin");
    }

    @Test
    void unUtenteNonProprietarioNonPuoAggiornareEnonPubblicaEventi() {
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi")));

        assertThatThrownBy(() -> documentoService.aggiorna(
                1L, "Tentativo", "Contenuto", utente("altro", Ruolo.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(protocollazioneProducer, never()).pubblica(any());
    }

    @Test
    void aggiornareUnDocumentoInesistenteLanciaEccezione() {
        when(documentoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentoService.aggiorna(
                99L, "x", "y", utente("mrossi", Ruolo.USER)))
                .isInstanceOf(RisorsaNonTrovataException.class);
    }

    // --- Aggiornamento dall'indice esterno ------------------------------------

    @Test
    void unAggiornamentoDallIndiceAllineaStatoEMarcaIndicizzato() {
        Documento documento = documentoEsistente(5L, "mrossi");
        when(documentoRepository.findById(5L)).thenReturn(Optional.of(documento));

        documentoService.applicaAggiornamentoIndice(
                new IndiceAggiornamentoEvent(5L, StatoDocumento.ARCHIVIATO, "motore-indicizzazione"));

        assertThat(documento.getStato()).isEqualTo(StatoDocumento.ARCHIVIATO);
        assertThat(documento.isIndicizzato()).isTrue();
        assertThat(documento.getDataIndicizzazione()).isNotNull();
    }

    @Test
    void unAggiornamentoDallIndicePerDocumentoInesistenteNonLanciaEccezioni() {
        when(documentoRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatCode(() -> documentoService.applicaAggiornamentoIndice(
                new IndiceAggiornamentoEvent(7L, StatoDocumento.ARCHIVIATO, "motore")))
                .doesNotThrowAnyException();
    }
}
