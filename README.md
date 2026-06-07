# Protocollo API

API REST di esempio per la **protocollazione di documenti**, costruita con Spring Boot.
Il progetto nasce come scheletro dimostrativo: mostra in modo compatto ma realistico
come mettere insieme autenticazione JWT, sicurezza, persistenza, messaggistica e test
in un'applicazione Java moderna.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)
![Build](https://img.shields.io/badge/build-Maven-blue)

---

## Cosa mostra il progetto

| Ambito           | Tecnologia / approccio                                                        |
|------------------|------------------------------------------------------------------------------|
| Linguaggio       | Java 21                                                                       |
| Framework        | Spring Boot 3.3 (Web, Validation, Data JPA, Security, Actuator)               |
| Sicurezza        | Spring Security con autenticazione **JWT** stateless e filter chain dedicata  |
| Autorizzazione   | Ruoli nel token, `@PreAuthorize` e `@AuthenticationPrincipal` per i permessi  |
| Persistenza      | Hibernate / JPA su **PostgreSQL**                                             |
| Migrazioni DB    | **Flyway** (schema versionato in SQL)                                         |
| Messaggistica    | **Kafka** (evento di protocollazione su POST e PUT)                           |
| Documentazione   | **OpenAPI / Swagger UI**                                                      |
| Osservabilita    | Logging con id di correlazione (MDC) + Spring Boot Actuator                   |
| Test             | **JUnit 5**, **Mockito**, MockMvc e **Testcontainers** per l'integrazione     |
| Build & Deploy   | Maven, Dockerfile multi-stage, Docker Compose                                 |

---

## Architettura

L'applicazione e organizzata a livelli, con responsabilita separate:

```
web (controller, DTO, gestione errori)
        |
        v
service (logica di business, permessi, eventi)
        |
        v
repository (Spring Data / Hibernate)  --->  PostgreSQL
        |
        +--> messaging (producer Kafka)  --->  Kafka
```

```
src/main/java/dev/protocollo
├── ProtocolloApplication.java        punto di ingresso
├── config/                           configurazioni (security, openapi, kafka, seeding)
├── security/                         JWT, filtro, UserDetails, principal
├── web/                              controller REST, DTO, exception handler
├── service/                          logica applicativa
├── repository/                       repository Spring Data JPA
├── domain/                           entita JPA ed enum
├── messaging/                        evento e producer Kafka
└── common/logging/                   filtro di logging con id di correlazione

src/main/resources
├── application.yml                   configurazione
├── logback-spring.xml                formato dei log
└── db/migration/                     migrazioni Flyway (V1, V2)
```

---

## Avvio rapido

### Prerequisiti
- JDK 21
- Maven 3.9+
- Docker (per l'infrastruttura locale e per i test di integrazione)

### 1. Avvia l'infrastruttura (PostgreSQL + Kafka)

```bash
docker compose up -d
```

Questo avvia PostgreSQL (porta 5432), Kafka (porta 9092) e una Kafka UI su
http://localhost:8081 per ispezionare i messaggi.

### 2. Avvia l'applicazione

```bash
mvn spring-boot:run
```

L'applicazione parte su http://localhost:8080. All'avvio:
- Flyway crea lo schema e inserisce i documenti di esempio;
- vengono creati due utenti di prova (vedi sotto).

### In alternativa: tutto in Docker

```bash
docker compose --profile app up -d --build
```

Avvia infrastruttura **e** applicazione, gia configurate per comunicare tra loro.

---

## Utenti di esempio

Creati automaticamente al primo avvio (password cifrate con BCrypt):

| Username | Password      | Ruoli         |
|----------|---------------|---------------|
| `admin`  | `admin123`    | ADMIN, USER   |
| `mrossi` | `password123` | USER          |

---

## Come si usa l'API

### 1. Login per ottenere il token

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

Risposta:

```json
{ "token": "eyJhbGciOi...", "tipo": "Bearer", "nome": "Amministratore di sistema" }
```

### 2. Chiamare gli endpoint protetti con il token

```bash
TOKEN="incolla-qui-il-token"

# Elenco documenti
curl http://localhost:8080/api/documenti -H "Authorization: Bearer $TOKEN"

# Creazione (genera un evento Kafka di protocollazione)
curl -X POST http://localhost:8080/api/documenti \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titolo":"Determina X","contenuto":"..."}'
```

Trovi tutte le chiamate pronte all'uso anche nel file [`api.http`](api.http).

### Endpoint disponibili

| Metodo | Percorso               | Descrizione                       | Permessi                  |
|--------|------------------------|-----------------------------------|---------------------------|
| POST   | `/api/auth/login`      | Login, restituisce il token JWT   | pubblico                  |
| GET    | `/api/documenti`       | Elenco paginato                   | autenticato               |
| GET    | `/api/documenti/{id}`  | Dettaglio singolo                 | autenticato               |
| POST   | `/api/documenti`       | Crea un documento + evento Kafka  | ruolo USER o ADMIN        |
| PUT    | `/api/documenti/{id}`  | Aggiorna + evento Kafka           | proprietario o ADMIN      |

### Documentazione interattiva (Swagger)

Con l'applicazione avviata: http://localhost:8080/swagger-ui.html
Usa il pulsante **Authorize** per inserire il token e provare gli endpoint dal browser.

---

## Come funziona l'autenticazione JWT

1. Il client invia username e password a `/api/auth/login`.
2. `AuthController` delega a Spring Security la verifica delle credenziali e, se
   valide, `JwtService` genera un token firmato (HMAC-SHA256) con ruoli e nome.
3. A ogni richiesta successiva il client invia l'header
   `Authorization: Bearer <token>`.
4. Il `JwtAuthenticationFilter` intercetta la richiesta, valida il token,
   ricarica l'utente dal database e popola il `SecurityContext`.
5. I controller leggono l'utente con `@AuthenticationPrincipal` e l'autorizzazione
   e gestita da `@PreAuthorize` (a grana grossa) e dalla logica del service
   (controllo del proprietario, a grana fine).

L'autenticazione e **stateless**: non esiste sessione lato server, lo stato vive
interamente nel token.

---

## Eventi Kafka

Alla creazione e all'aggiornamento di un documento viene pubblicato un evento sul
topic `protocollo.documenti.protocollazione`. L'invio e **asincrono e non
bloccante**: se il broker non e raggiungibile la richiesta HTTP va comunque a buon
fine e l'errore viene solo registrato nei log (cosi l'esempio resta eseguibile
anche senza Kafka).

Esempio di messaggio (JSON):

```json
{
  "idDocumento": 3,
  "titolo": "Determina X",
  "numeroProtocollo": "PRT-2026-000003",
  "proprietario": "admin",
  "operazione": "CREAZIONE",
  "timestamp": "2026-06-07T10:15:30Z"
}
```

Puoi vedere i messaggi nella Kafka UI su http://localhost:8081.

---

## Test

Il progetto distingue tra test veloci (unitari) e test di integrazione.

```bash
# Solo unit test (veloci, non serve Docker)
mvn test

# Unit test + test di integrazione con Testcontainers (serve Docker attivo)
mvn verify
```

- **Unit test** (`*Test`): `JwtServiceTest`, `DocumentoServiceTest` (Mockito),
  `DocumentoControllerTest` (MockMvc sul solo livello web).
- **Test di integrazione** (`*IT`): `DocumentoIntegrationIT` avvia PostgreSQL e
  Kafka reali con Testcontainers ed esercita l'intero flusso login -> POST -> PUT -> GET.

---

## Scelte tecniche

- **DTO separati dalle entita**: il modello di persistenza non viene esposto
  direttamente all'esterno.
- **`ddl-auto: validate`**: lo schema e gestito solo da Flyway; Hibernate si limita
  a verificare la corrispondenza con le entita, senza modificare il database.
- **Lock ottimistico** (`@Version`) sui documenti per gestire le modifiche concorrenti.
- **Gestione errori centralizzata** con `@RestControllerAdvice` e risposte in
  formato `ProblemDetail` (RFC 7807).
- **Niente Lombok**: il codice e volutamente esplicito (record per i DTO,
  getter/setter sulle entita) per restare leggibile a chi lo studia.

---

## Possibili estensioni

Spunti per chi volesse ampliare l'esempio: refresh token, paginazione e filtri di
ricerca piu ricchi, un consumer Kafka, cache, rate limiting, profili `dev`/`prod`
distinti, pipeline CI/CD e manifest Kubernetes.

---

Progetto dimostrativo a scopo didattico e di portfolio.
