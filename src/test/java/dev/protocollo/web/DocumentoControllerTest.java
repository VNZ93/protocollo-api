package dev.protocollo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.protocollo.domain.Documento;
import dev.protocollo.domain.Ruolo;
import dev.protocollo.domain.StatoDocumento;
import dev.protocollo.domain.Utente;
import dev.protocollo.security.CustomUserDetailsService;
import dev.protocollo.security.JwtService;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.service.DocumentoService;
import dev.protocollo.web.dto.DocumentoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test del solo livello web ({@link DocumentoController}) tramite MockMvc.
 *
 * Il service e mockato: qui si verificano routing, serializzazione JSON,
 * codici di stato e validazione, non la logica di business.
 * L'utente autenticato viene simulato con il post-processor {@code user(...)}.
 */
@WebMvcTest(DocumentoController.class)
class DocumentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentoService documentoService;

    // Il JwtAuthenticationFilter e un bean Filter incluso da @WebMvcTest:
    // queste sue dipendenze vanno fornite (mockate) per far caricare il contesto.
    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private UtenteAutenticato principal() {
        Utente utente = new Utente("mrossi", "hash", "Mario Rossi", Set.of(Ruolo.USER));
        return new UtenteAutenticato(utente);
    }

    private Documento documentoDiEsempio() {
        Documento documento = new Documento("Titolo", "Contenuto", "mrossi");
        ReflectionTestUtils.setField(documento, "id", 1L);
        documento.setNumeroProtocollo("PRT-2026-000001");
        documento.setStato(StatoDocumento.PROTOCOLLATO);
        return documento;
    }

    @Test
    void getDettaglioRestituisceIlDocumento() throws Exception {
        when(documentoService.trova(1L)).thenReturn(documentoDiEsempio());

        mockMvc.perform(get("/api/documenti/1").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titolo").value("Titolo"))
                .andExpect(jsonPath("$.numeroProtocollo").value("PRT-2026-000001"));
    }

    @Test
    void postCreaRestituisce201ConIlDocumentoCreato() throws Exception {
        when(documentoService.crea(eq("Titolo"), eq("Contenuto"), any()))
                .thenReturn(documentoDiEsempio());

        DocumentoRequest richiesta = new DocumentoRequest("Titolo", "Contenuto");

        mockMvc.perform(post("/api/documenti")
                        .with(user(principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(richiesta)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stato").value("PROTOCOLLATO"));
    }

    @Test
    void postSenzaTitoloRestituisce400() throws Exception {
        // Titolo vuoto: la validazione @NotBlank deve far fallire la richiesta
        DocumentoRequest richiesta = new DocumentoRequest("", "Contenuto");

        mockMvc.perform(post("/api/documenti")
                        .with(user(principal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(richiesta)))
                .andExpect(status().isBadRequest());
    }
}
