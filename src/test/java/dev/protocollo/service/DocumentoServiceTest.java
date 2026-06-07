package dev.protocollo.service;

import dev.protocollo.domain.Documento;
import dev.protocollo.domain.Ruolo;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.domain.Utente;
import dev.protocollo.messaging.ProtocollazioneEvent;
import dev.protocollo.messaging.ProtocollazioneProducer;
import dev.protocollo.repository.DocumentoRepository;
import dev.protocollo.security.UtenteAutenticato;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitari della logica di business in {@link DocumentoService}.
 *
 * Le dipendenze (repository e producer Kafka) sono sostituite da mock Mockito,
 * cosi da testare solo le regole del service in isolamento e senza database.
 */
@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private ProtocollazioneProducer protocollazioneProducer;

    @InjectMocks
    private DocumentoService documentoService;

    // --- Utenti di test -------------------------------------------------------

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

    // --- Creazione ------------------------------------------------------------

    @Test
    void laCreazioneAssegnaProtocolloProprietarioEPubblicaEvento() {
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
        when(documentoRepository.findById(1L))
                .thenReturn(Optional.of(documentoEsistente(1L, "mrossi")));

        Documento risultato = documentoService.aggiorna(
                1L, "Titolo aggiornato", "Nuovo contenuto", utente("mrossi", Ruolo.USER));

        assertThat(risultato.getTitolo()).isEqualTo("Titolo aggiornato");
        verify(protocollazioneProducer).pubblica(any(ProtocollazioneEvent.class));
    }

    @Test
    void unAmministratorePuoAggiornareDocumentiAltrui() {
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
}
