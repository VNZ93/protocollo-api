package dev.protocollo;

import dev.protocollo.web.dto.DocumentoRequest;
import dev.protocollo.web.dto.DocumentoResponse;
import dev.protocollo.web.dto.LoginRequest;
import dev.protocollo.web.dto.LoginResponse;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test di integrazione "end-to-end" che esercita l'intero stack:
 * controller, sicurezza JWT, service, repository Hibernate, Flyway, Kafka.
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

    @Autowired
    private TestRestTemplate restTemplate;

    /** Effettua il login e restituisce gli header con il token Bearer. */
    private HttpHeaders headerAutenticati(String username, String password) {
        ResponseEntity<LoginResponse> login = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, password), LoginResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().token());
        return headers;
    }

    @Test
    void senzaTokenLaListaDeiDocumentiRestituisce401() {
        ResponseEntity<String> risposta = restTemplate.getForEntity("/api/documenti", String.class);
        assertThat(risposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void flussoCompletoLoginCreazioneEAggiornamento() {
        HttpHeaders headers = headerAutenticati("mrossi", "password123");

        // --- Creazione (POST) ---
        DocumentoRequest creazione = new DocumentoRequest("Documento di test", "Contenuto iniziale");
        ResponseEntity<DocumentoResponse> creato = restTemplate.exchange(
                "/api/documenti", HttpMethod.POST,
                new HttpEntity<>(creazione, headers), DocumentoResponse.class);

        assertThat(creato.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creato.getBody()).isNotNull();
        assertThat(creato.getBody().proprietario()).isEqualTo("mrossi");
        assertThat(creato.getBody().numeroProtocollo()).startsWith("PRT-");
        Long idCreato = creato.getBody().id();

        // --- Aggiornamento (PUT) ---
        DocumentoRequest aggiornamento = new DocumentoRequest("Titolo aggiornato", "Contenuto modificato");
        ResponseEntity<DocumentoResponse> aggiornato = restTemplate.exchange(
                "/api/documenti/" + idCreato, HttpMethod.PUT,
                new HttpEntity<>(aggiornamento, headers), DocumentoResponse.class);

        assertThat(aggiornato.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aggiornato.getBody()).isNotNull();
        assertThat(aggiornato.getBody().titolo()).isEqualTo("Titolo aggiornato");

        // --- Lettura del dettaglio (GET) ---
        ResponseEntity<DocumentoResponse> letto = restTemplate.exchange(
                "/api/documenti/" + idCreato, HttpMethod.GET,
                new HttpEntity<>(headers), DocumentoResponse.class);

        assertThat(letto.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(letto.getBody()).isNotNull();
        assertThat(letto.getBody().titolo()).isEqualTo("Titolo aggiornato");
    }
}
