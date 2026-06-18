package dev.protocollo;

import dev.protocollo.web.dto.DocumentoRequest;
import dev.protocollo.web.dto.DocumentoResponse;
import dev.protocollo.web.dto.LoginRequest;
import dev.protocollo.web.dto.RefreshRequest;
import dev.protocollo.web.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test di integrazione "end-to-end" che esercita l'intero stack:
 * controller, sicurezza JWT, refresh token, service, repository Hibernate,
 * Flyway, generazione PDF su storage locale (profilo dev) e Kafka.
 *
 * Avvia PostgreSQL e Kafka reali tramite Testcontainers (serve Docker attivo).
 * Le annotazioni {@code @ServiceConnection} collegano automaticamente i
 * container alla configurazione di Spring Boot (datasource e bootstrap Kafka).
 *
 * Il suffisso "IT" fa si che venga eseguito da Maven nella fase "verify"
 * (plugin failsafe), non insieme agli unit test piu veloci.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DocumentoIntegrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    /**
     * Accorcio il ritardo e l'intervallo di scansione della protocollazione
     * automatica, oltre all'intervallo di polling dell'outbox: altrimenti il
     * test dovrebbe attendere i 60 secondi reali di default per vedere il
     * round-trip job->outbox->Kafka->consumer completarsi (l'outbox da solo
     * pubblica ogni 5s di default, che sommati alla latenza dei runner CI
     * possono far scadere il timeout di attendiStato).
     */
    @DynamicPropertySource
    static void proprietaProtocollazione(DynamicPropertyRegistry registry) {
        registry.add("app.protocollazione.ritardo-secondi", () -> "1");
        registry.add("app.protocollazione.intervallo-controllo-ms", () -> "500");
        registry.add("app.outbox.polling-delay", () -> "300");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    /** Effettua il login e restituisce la coppia di token. */
    private TokenResponse login(String username, String password) {
        ResponseEntity<TokenResponse> risposta = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, password), TokenResponse.class);

        assertThat(risposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(risposta.getBody()).isNotNull();
        return risposta.getBody();
    }

    /** Header con l'access token in formato Bearer. */
    private HttpHeaders headerConToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void senzaTokenLaListaDeiDocumentiRestituisce401() {
        ResponseEntity<String> risposta = restTemplate.getForEntity("/api/documenti", String.class);
        assertThat(risposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void flussoCompletoBozzaApprovazioneProtocollazioneAutomaticaEDownloadPdf() {
        HttpHeaders headersUtente = headerConToken(login("mrossi", "password123").accessToken());

        // --- Creazione: produce una bozza, senza protocollo ne PDF ---
        DocumentoRequest creazione = new DocumentoRequest("Documento di test", "Contenuto iniziale");
        ResponseEntity<DocumentoResponse> creato = restTemplate.exchange(
                "/api/documenti", HttpMethod.POST,
                new HttpEntity<>(creazione, headersUtente), DocumentoResponse.class);

        assertThat(creato.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creato.getBody()).isNotNull();
        assertThat(creato.getBody().proprietario()).isEqualTo("mrossi");
        assertThat(creato.getBody().stato().name()).isEqualTo("BOZZA");
        assertThat(creato.getBody().numeroProtocollo()).isNull();
        assertThat(creato.getBody().pdfRiferimento()).isNull();
        Long idCreato = creato.getBody().id();

        // --- Aggiornamento (PUT): consentito finche e ancora in bozza ---
        DocumentoRequest aggiornamento = new DocumentoRequest("Titolo aggiornato", "Contenuto modificato");
        ResponseEntity<DocumentoResponse> aggiornato = restTemplate.exchange(
                "/api/documenti/" + idCreato, HttpMethod.PUT,
                new HttpEntity<>(aggiornamento, headersUtente), DocumentoResponse.class);

        assertThat(aggiornato.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aggiornato.getBody()).isNotNull();
        assertThat(aggiornato.getBody().titolo()).isEqualTo("Titolo aggiornato");

        // --- Solo un amministratore puo approvare ---
        ResponseEntity<String> approvazioneVietata = restTemplate.exchange(
                "/api/documenti/" + idCreato + "/approva", HttpMethod.POST,
                new HttpEntity<>(headersUtente), String.class);
        assertThat(approvazioneVietata.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // --- Approvazione (solo admin) ---
        HttpHeaders headersAdmin = headerConToken(login("admin", "admin123").accessToken());
        ResponseEntity<DocumentoResponse> approvato = restTemplate.exchange(
                "/api/documenti/" + idCreato + "/approva", HttpMethod.POST,
                new HttpEntity<>(headersAdmin), DocumentoResponse.class);

        assertThat(approvato.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approvato.getBody()).isNotNull();
        assertThat(approvato.getBody().stato().name()).isEqualTo("APPROVATA");
        assertThat(approvato.getBody().dataApprovazione()).isNotNull();

        // --- Una volta approvato, non e piu modificabile ---
        ResponseEntity<String> aggiornamentoVietato = restTemplate.exchange(
                "/api/documenti/" + idCreato, HttpMethod.PUT,
                new HttpEntity<>(aggiornamento, headersUtente), String.class);
        assertThat(aggiornamentoVietato.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // --- Protocollazione automatica: job di scansione -> Kafka -> consumer ---
        DocumentoResponse protocollato = attendiStato(idCreato, headersAdmin, "PROTOCOLLATO");
        assertThat(protocollato.numeroProtocollo()).startsWith("PRT-");
        assertThat(protocollato.pdfRiferimento()).isNotBlank();

        // --- Download del PDF (GET) ---
        ResponseEntity<byte[]> pdf = restTemplate.exchange(
                "/api/documenti/" + idCreato + "/pdf", HttpMethod.GET,
                new HttpEntity<>(headersAdmin), byte[].class);

        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getBody()).isNotEmpty();
        // I file PDF iniziano con la firma "%PDF"
        assertThat(new String(pdf.getBody(), 0, 4)).isEqualTo("%PDF");

        // --- Archiviazione: tag indipendente dallo stato ---
        ResponseEntity<DocumentoResponse> archiviato = restTemplate.exchange(
                "/api/documenti/" + idCreato + "/archivia", HttpMethod.POST,
                new HttpEntity<>(headersUtente), DocumentoResponse.class);

        assertThat(archiviato.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archiviato.getBody()).isNotNull();
        assertThat(archiviato.getBody().archiviato()).isTrue();
        assertThat(archiviato.getBody().stato().name()).isEqualTo("PROTOCOLLATO");
    }

    /**
     * Effettua il polling del documento finche raggiunge lo stato atteso o
     * scade il timeout: la protocollazione automatica avviene in background
     * (job schedulato + round-trip Kafka), non in risposta a una singola richiesta.
     */
    private DocumentoResponse attendiStato(Long id, HttpHeaders headers, String statoAtteso) {
        Instant scadenza = Instant.now().plusSeconds(25);
        while (Instant.now().isBefore(scadenza)) {
            ResponseEntity<DocumentoResponse> risposta = restTemplate.exchange(
                    "/api/documenti/" + id, HttpMethod.GET,
                    new HttpEntity<>(headers), DocumentoResponse.class);
            DocumentoResponse corpo = risposta.getBody();
            if (corpo != null && statoAtteso.equals(corpo.stato().name())) {
                return corpo;
            }
            try {
                Thread.sleep(Duration.ofMillis(300));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Il documento " + id + " non ha raggiunto lo stato " + statoAtteso
                + " entro il timeout");
    }

    @Test
    void ilRefreshTokenRilasciaUnNuovoAccessToken() {
        TokenResponse iniziale = login("admin", "admin123");

        ResponseEntity<TokenResponse> refresh = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(iniziale.refreshToken()), TokenResponse.class);

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh.getBody()).isNotNull();
        assertThat(refresh.getBody().accessToken()).isNotBlank();
        // La rotazione emette un refresh token diverso dal precedente
        assertThat(refresh.getBody().refreshToken()).isNotEqualTo(iniziale.refreshToken());

        // Il nuovo access token permette di accedere a una risorsa protetta
        ResponseEntity<String> lista = restTemplate.exchange(
                "/api/documenti", HttpMethod.GET,
                new HttpEntity<>(headerConToken(refresh.getBody().accessToken())), String.class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
