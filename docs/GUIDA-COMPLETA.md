# Guida completa al progetto Protocollo API

> Compendio di studio. Spiega il progetto dall'inizio alla fine: concetti di base,
> configurazione, e poi ogni classe con i suoi metodi e le righe piu importanti.
> Pensato per poterlo presentare a un colloquio senza esitazioni.

Per la visione d'insieme vedi [HLD.md](HLD.md) e [LLD.md](LLD.md); per i pattern
[PATTERNS.md](PATTERNS.md).

## Come usare questa guida

Ogni classe e spiegata con questo schema:
- **Cosa fa** (lo scopo in una frase).
- **Perche esiste** (la motivazione di design).
- **Spiegazione** di campi e metodi, con le righe chiave commentate.
- **Domande da colloquio** dove l'argomento e tipico.

---

## Indice

- [Parte 0 - Concetti di base](#parte-0---concetti-di-base)
- [Parte 1 - Build e configurazione](#parte-1---build-e-configurazione)
- [Parte 2 - Il codice, classe per classe](#parte-2---il-codice-classe-per-classe)
  - [Avvio](#avvio)
  - [Dominio (entita ed enum)](#dominio-entita-ed-enum)
  - [Repository](#repository)
  - [Sicurezza](#sicurezza)
  - [Configurazione](#configurazione-classi-config)
  - [Web (controller e DTO)](#web-controller-e-dto)
  - [Service](#service)
  - [Messaging e Outbox](#messaging-e-outbox)
  - [PDF](#pdf)
  - [Storage](#storage)
  - [Client REST esterni](#client-rest-esterni)
  - [Common (filtri trasversali)](#common-filtri-trasversali)
- [Parte 3 - Flussi end-to-end](#parte-3---flussi-end-to-end)
- [Parte 4 - Domande di colloquio](#parte-4---domande-di-colloquio)

---

# Parte 0 - Concetti di base

Ripasso veloce dei concetti su cui poggia il progetto. Servono per spiegare il
"perche" delle scelte.

### Inversion of Control (IoC) e Dependency Injection (DI)
Le classi non creano le proprie dipendenze (`new ...`), le **ricevono dall'esterno**.
In Spring, un contenitore (l'`ApplicationContext`) crea gli oggetti gestiti
(i **bean**) e li "inietta" dove servono, di norma tramite il **costruttore**.
Vantaggi: codice disaccoppiato e testabile (nei test passo dei finti, i mock).

### Bean e stereotipi
Un **bean** e un oggetto gestito da Spring. Le annotazioni di stereotipo dicono a
Spring di crearlo: `@Component` (generico), `@Service` (logica), `@Repository`
(accesso dati), `@RestController` (web), `@Configuration` (definisce altri bean
con `@Bean`). Di default i bean sono **singleton** (una sola istanza condivisa).

### Spring Boot e auto-configuration
Spring Boot configura automaticamente molte cose in base alle dipendenze presenti
nel classpath (es. se c'e PostgreSQL configura il DataSource). Si parte da
`@SpringBootApplication` e si sovrascrive solo cio che serve, via `application.yml`.

### JPA e Hibernate
**JPA** e lo standard Java per mappare oggetti su tabelle (ORM). **Hibernate** ne e
l'implementazione. Un'**entita** (`@Entity`) corrisponde a una tabella; un
**repository** Spring Data offre i metodi CRUD senza scrivere SQL. Il
**persistence context** tiene traccia delle entita "managed": modificandone i
campi dentro una transazione, Hibernate genera l'UPDATE da solo (**dirty checking**).

### Transazioni
`@Transactional` racchiude un blocco di operazioni sul DB: o vanno a buon fine
tutte (commit) o nessuna (rollback). Fondamentale per la coerenza (es. salvare
documento ed evento outbox insieme).

### JWT (JSON Web Token)
Token firmato composto da header, payload (i **claim**, es. utente e ruoli) e
firma. Chi lo riceve ne verifica la firma con una chiave segreta: se valida, si
fida del contenuto senza interrogare un database. Abilita autenticazione
**stateless** (nessuna sessione server).

### Kafka
Sistema di messaggi a **topic**. Un **producer** pubblica messaggi, un **consumer**
li legge. Disaccoppia i sistemi: chi produce non sa chi consuma.

### Profili Spring
Permettono configurazioni diverse per ambiente (`dev`, `prod`). Un bean annotato
`@Profile("prod")` esiste solo con quel profilo attivo.

---

# Parte 1 - Build e configurazione

### pom.xml (Maven)
E il descrittore del progetto: eredita dal **parent** di Spring Boot (che allinea
le versioni delle librerie) e dichiara le dipendenze. Gruppi principali:
- **web**: `spring-boot-starter-web` (REST + Tomcat) e `-validation` (Bean Validation).
- **persistenza**: `-data-jpa` (Hibernate), driver `postgresql`, `flyway-core` e
  `flyway-database-postgresql` (migrazioni).
- **sicurezza**: `-security` + `jjwt-api/impl/jackson` (JWT).
- **messaging**: `spring-kafka`.
- **cache**: `-cache` + `caffeine`.
- **PDF**: `openpdf` + `flying-saucer-pdf-openpdf` (HTML -> PDF).
- **object storage**: AWS SDK v2 `s3` + `apache-client` (profilo prod).
- **osservabilita**: `-actuator`, `springdoc-openapi` (Swagger).
- **test**: `-test` (JUnit5, Mockito), `spring-security-test`, Testcontainers.

Sezione `build`:
- **spring-boot-maven-plugin**: crea il JAR eseguibile.
- **surefire**: esegue gli unit test `*Test` (fase `test`), escludendo gli `*IT`.
- **failsafe**: esegue i test di integrazione `*IT` (fase `verify`).

Domanda tipica: *perche surefire e failsafe separati?* Per tenere veloci i test
unitari e far girare quelli pesanti (Testcontainers, Docker) solo in `verify`.

### application.yml
Configurazione comune. Punti chiave:
- `spring.profiles.active: dev` di default.
- `datasource`: URL/credenziali PostgreSQL (sovrascrivibili da env).
- `jpa.hibernate.ddl-auto: validate`: Hibernate **valida** lo schema, non lo crea
  (lo schema lo fa Flyway). `open-in-view: false` evita di tenere aperta la
  sessione JPA nel layer web (buona pratica). `jdbc.time_zone: UTC`.
- `flyway`: abilitato, migrazioni in `classpath:db/migration`.
- `kafka.producer`: serializza i valori in JSON. `kafka.consumer`: usa
  `ErrorHandlingDeserializer` (un messaggio rotto non blocca il consumo) con tipo
  di default `IndiceAggiornamentoEvent`.
- `app.*`: proprieta personalizzate (jwt, kafka topic, outbox, rate-limit,
  accreditamento, servizio-anagrafica).
- `management.endpoints`: espone health/info/metrics.

`application-dev.yml` definisce lo storage locale; `application-prod.yml` lo storage
S3/MinIO. La scelta dell'implementazione avviene per profilo (vedi `storage`).

### logback-spring.xml
Formato dei log. Il pattern include `%X{requestId}`: l'id di correlazione messo
nell'MDC dal `RequestLoggingFilter`, utile per seguire una richiesta su Kibana/ELK.

### Migrazioni Flyway (db/migration)
File SQL numerati, eseguiti in ordine una sola volta:
- **V1**: tabelle `utente`, `utente_ruolo`, `documento`.
- **V2**: documenti di esempio.
- **V3**: colonne PDF/indicizzazione su `documento`.
- **V4**: tabella `refresh_token`.
- **V5**: colonna `email` su `utente`.
- **V6**: tabella `outbox_event`.

Regola d'oro: una migrazione gia applicata **non si modifica**; si aggiunge una
nuova migrazione. Flyway tiene traccia in una tabella `flyway_schema_history`.

### Dockerfile e docker-compose
- **Dockerfile**: multi-stage. Stage 1 (Maven+JDK) compila il JAR; stage 2 (solo
  JRE) lo esegue come utente non privilegiato. Immagine finale leggera.
- **docker-compose.yml**: avvia PostgreSQL, Kafka (KRaft), Kafka UI e MinIO; con il
  profilo `app` avvia anche l'applicazione (in profilo Spring `prod`).

---

# Parte 2 - Il codice, classe per classe

## Avvio

### ProtocolloApplication
**Cosa fa**: e il punto di ingresso; avvia il contesto Spring.
**Spiegazione delle annotazioni**:
- `@SpringBootApplication`: attiva auto-configuration, component scan (da
  `dev.protocollo` in giu) e definizione di bean locali.
- `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`: serializza le
  `Page` in un JSON stabile e documentato, invece dell'oggetto interno `PageImpl`.
- `@ConfigurationPropertiesScan`: registra le classi `@ConfigurationProperties`
  (es. `AccreditamentoProperties`).
- `@EnableScheduling`: abilita i `@Scheduled` (il publisher dell'outbox).

`main` chiama `SpringApplication.run(...)`: avvia Tomcat, crea i bean, esegue Flyway
e i `CommandLineRunner` (il `DataSeeder`).

**Domanda**: *cosa fa @SpringBootApplication?* E una meta-annotazione che combina
`@Configuration`, `@EnableAutoConfiguration` e `@ComponentScan`.

---

## Dominio (entita ed enum)

### Ruolo (enum)
Due valori: `USER` e `ADMIN`. Convenzione di Spring Security: l'authority del
ruolo si scrive `ROLE_USER`/`ROLE_ADMIN` (il prefisso `ROLE_` lo aggiungiamo noi
in `UtenteAutenticato`).

### StatoDocumento (enum)
Ciclo di vita del documento: `BOZZA`, `PROTOCOLLATO`, `ARCHIVIATO`.

### Documento (entita)
**Cosa fa**: rappresenta un documento da protocollare; mappa la tabella `documento`.
**Campi principali**:
- `id`: chiave primaria, `@GeneratedValue(IDENTITY)` (la genera il DB).
- `titolo`, `contenuto` (`TEXT`), `stato` (`@Enumerated(STRING)`: salva il nome,
  non l'ordinale, piu robusto).
- `numeroProtocollo`: valorizzato alla protocollazione.
- `proprietario`: username di chi l'ha creato (usato per i permessi).
- `pdfRiferimento`: chiave del PDF sullo storage.
- `indicizzato` + `dataIndicizzazione`: aggiornati dal consumer dell'indice.
- `dataCreazione`/`dataAggiornamento`: date di audit.
- `version` (`@Version`): **lock ottimistico**. Hibernate incrementa la versione a
  ogni update e, se due transazioni modificano la stessa riga, lancia
  un'eccezione invece di sovrascrivere silenziosamente.

**Callback del ciclo di vita**:
- `@PrePersist prePersist()`: prima dell'INSERT imposta le date e lo stato di default.
- `@PreUpdate preUpdate()`: prima di ogni UPDATE aggiorna `dataAggiornamento`.

Il costruttore vuoto `protected Documento()` e richiesto da Hibernate (che
istanzia l'entita via reflection). Getter/setter espongono i campi in modo
controllato (alcuni hanno solo il getter: id, date, version sono gestiti dal framework).

**Domande**: *perche EnumType.STRING e non ORDINAL?* Perche aggiungere/riordinare i
valori dell'enum non rompe i dati esistenti. *Cos'e il lock ottimistico?* Vedi `@Version`.

### Utente (entita)
Mappa la tabella `utente`. Campi: `username` (unico), `password` (hash BCrypt, mai
in chiaro), `nomeCompleto`, `email`, `attivo` (per disabilitare senza cancellare),
`ruoli`. I ruoli sono una `@ElementCollection` (tabella `utente_ruolo`) caricata
`EAGER` perche servono a ogni autenticazione. `email` ha solo il setter perche
viene valorizzata dopo la costruzione (nel `DataSeeder`).

### RefreshToken (entita)
Token opaco persistito (tabella `refresh_token`): `token` (stringa casuale unica),
`username`, `scadenza`, `revocato`. Il metodo `isUtilizzabile()` ritorna vero se non
revocato e non scaduto. **Perche persistito** e non un JWT? Per poterlo **revocare**
(logout, rotazione): un JWT stateless non si puo invalidare prima della scadenza.

### OutboxEvent (entita)
Riga della tabella outbox: `tipo` (es. "PROTOCOLLAZIONE", per sapere come
deserializzare), `aggregateId`, `topic`, `chiave`, `payload` (JSON dell'evento),
`creatoIl`, `pubblicato`, `pubblicatoIl`. Il metodo `segnaPubblicato()` marca
l'evento come inviato. `@PrePersist` imposta `creatoIl`.

---

## Repository

### UtenteRepository
`extends JpaRepository<Utente, Long>`: eredita CRUD. Aggiunge
`Optional<Utente> findByUsername(String)`: **query derivata** dal nome del metodo
(Spring Data genera la query automaticamente).

### DocumentoRepository
`extends JpaRepository<Documento, Long>, JpaSpecificationExecutor<Documento>`.
Mostra tre stili di query:
- derivate dal nome: `countByProprietario`, `findByStato`;
- **JPQL** esplicita con `@Query` (`cercaPerTitolo`);
- **Specification** (dinamiche) tramite `JpaSpecificationExecutor`.

### FiltroDocumenti (record)
Contenitore dei criteri di ricerca, tutti opzionali: `stato`, `proprietario`,
`testo`, `creatoDa`, `creatoA`.

### DocumentoSpecifications
**Cosa fa**: costruisce dinamicamente la `WHERE` a partire dai filtri presenti.
Il metodo `daFiltro(filtro)` ritorna una `Specification<Documento>`: dentro la
lambda `(root, query, cb)` costruisce una lista di `Predicate`, aggiungendo solo i
criteri valorizzati (controllo `!= null`/`hasText`), e li combina in AND con
`cb.and(...)`. Se la lista e vuota, nessun vincolo (ritorna tutto). E il pattern
**Specification**: criteri componibili e type-safe, invece di una query per ogni
combinazione di filtri.

### RefreshTokenRepository / OutboxEventRepository
- `RefreshTokenRepository`: `findByToken(String)`.
- `OutboxEventRepository`: `findTop50ByPubblicatoFalseOrderByIdAsc()` recupera i
  primi 50 eventi non pubblicati in ordine di inserimento (il limite evita di
  caricare un backlog enorme in memoria).

---

## Sicurezza

### UtenteAutenticato (principal)
**Cosa fa**: adatta la nostra entita `Utente` all'interfaccia `UserDetails` di
Spring Security. E l'oggetto che finisce nel SecurityContext e che i controller
ricevono con `@AuthenticationPrincipal`.
**Punti chiave**:
- Nel costruttore copia i dati dell'utente e converte i ruoli in authority:
  `new SimpleGrantedAuthority("ROLE_" + ruolo.name())`. Qui si aggiunge il prefisso
  `ROLE_`.
- `isAmministratore()`: comodita per i controlli di permesso nel service.
- Implementa i metodi di `UserDetails` (`getAuthorities`, `getPassword`,
  `getUsername`, `isEnabled` -> ritorna `attivo`, gli altri `true`).
E il pattern **Adapter**.

### CustomUserDetailsService
Implementa `UserDetailsService`. Il metodo `loadUserByUsername` cerca l'utente nel
DB e lo avvolge in `UtenteAutenticato`, oppure lancia `UsernameNotFoundException`.
Spring Security lo usa sia al login (per verificare la password) sia nel filtro JWT
(per ricaricare l'utente a ogni richiesta, intercettando utenti disabilitati).

### JwtService (in dettaglio)
**Cosa fa**: crea e valida i token JWT con firma HMAC-SHA256.

Costruttore:
```java
public JwtService(@Value("${app.jwt.secret}") String secret,
                  @Value("${app.jwt.expiration-minutes}") long durataMinuti) {
    this.chiaveFirma = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.durataMillis = durataMinuti * 60_000L;
}
```
- `@Value`: inietta i valori da `application.yml`.
- `Keys.hmacShaKeyFor`: crea la chiave segreta HMAC dai byte del secret (che deve
  essere lungo almeno 32 byte = 256 bit, requisito di HMAC-SHA256).
- `durataMillis`: minuti convertiti in millisecondi.

Generazione:
```java
public String generaToken(UtenteAutenticato utente) {
    Instant adesso = Instant.now();
    List<String> ruoli = utente.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
            .toList();
    return Jwts.builder()
            .subject(utente.getUsername())     // claim "sub"
            .claim("ruoli", ruoli)             // claim custom
            .claim("nome", utente.getNomeCompleto())
            .issuedAt(Date.from(adesso))       // "iat"
            .expiration(Date.from(adesso.plusMillis(durataMillis))) // "exp"
            .signWith(chiaveFirma)             // firma HMAC
            .compact();                        // produce la stringa compatta
}
```
Toglie il prefisso `ROLE_` per mettere nel claim i nomi "puri" dei ruoli. Usa il
**Builder** di JJWT.

Validazione:
```java
public boolean isValido(String token) {
    try { leggiClaim(token); return true; }
    catch (Exception e) { return false; }
}
private Claims leggiClaim(String token) {
    return Jwts.parser().verifyWith(chiaveFirma).build()
            .parseSignedClaims(token).getPayload();
}
```
`parseSignedClaims` verifica firma e scadenza: se qualcosa non torna lancia
un'eccezione, intercettata da `isValido` che ritorna `false`. `estraiUsername`
ritorna il `subject`.

**Domande**: *dove sta lo stato della sessione?* Nel token (stateless). *Perche il
secret deve essere lungo?* Requisito di HMAC-SHA256 (chiave >= 256 bit). *Cosa
succede se il token e scaduto?* `parseSignedClaims` lancia eccezione -> 401.

### JwtAuthenticationFilter (in dettaglio)
**Cosa fa**: a ogni richiesta legge l'header `Authorization: Bearer <token>`,
valida il JWT e popola il SecurityContext.
Estende `OncePerRequestFilter` (pattern **Template Method**: implemento solo
`doFilterInternal`, garantita una sola esecuzione per richiesta).

```java
String token = estraiToken(request);
if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
    filterChain.doFilter(request, response); return;   // niente token: prosegui
}
if (!jwtService.isValido(token)) { filterChain.doFilter(request, response); return; }
String username = jwtService.estraiUsername(token);
UserDetails utente = userDetailsService.loadUserByUsername(username); // ricarico dal DB
UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(utente, null, utente.getAuthorities());
auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
SecurityContextHolder.getContext().setAuthentication(auth); // ora la richiesta e autenticata
filterChain.doFilter(request, response);
```
Note di design:
- Se non c'e token, **non** blocca: lascia decidere alle regole di autorizzazione
  (le rotte pubbliche passano, le altre verranno respinte con 401).
- Ricarica l'utente dal DB a ogni richiesta: cosi un utente disabilitato viene
  bloccato anche se ha un token ancora valido (trade-off: una query in piu).
- `estraiToken` rimuove il prefisso `"Bearer "` dall'header.

### SecurityConfig (in dettaglio)
**Cosa fa**: definisce la filter chain di sicurezza e i bean correlati.
`@EnableMethodSecurity` abilita `@PreAuthorize` sui metodi.

```java
http.csrf(disable)                                  // API stateless, niente cookie
    .sessionManagement(STATELESS)                   // nessuna sessione server
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(ROTTE_PUBBLICHE).permitAll() // login, swagger, health
        .anyRequest().authenticated())              // tutto il resto: autenticato
    .exceptionHandling(ep -> ep.authenticationEntryPoint(entryPoint())) // 401 invece di redirect
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```
Bean dichiarati:
- `passwordEncoder()` -> `BCryptPasswordEncoder` (hash con salt).
- `authenticationManager(...)` -> esposto per il login (usa il
  `CustomUserDetailsService` + `PasswordEncoder` configurati da Boot).
- `registrazioneFiltroJwt(...)` -> un `FilterRegistrationBean` con `setEnabled(false)`:
  serve a **disabilitare la registrazione automatica** del `JwtAuthenticationFilter`
  come filtro servlet globale, perche deve girare solo dentro la catena di
  sicurezza (altrimenti verrebbe eseguito due volte).

**Domande**: *perche CSRF disabilitato?* Perche l'API e stateless e non usa cookie
di sessione (il token va in un header, non e inviato automaticamente dal browser).
*Differenza autenticazione/autorizzazione?* La prima stabilisce **chi sei**, la
seconda **cosa puoi fare**. *Dove avviene l'autorizzazione fine?* Nel service
(controllo proprietario), oltre a `@PreAuthorize` per i ruoli.

---

## Configurazione (classi config)

### OpenApiConfig
Definisce i metadati OpenAPI e lo **schema di sicurezza** "bearer JWT", cosi che
Swagger UI mostri il pulsante "Authorize" per inserire il token e provare le API
protette. Bean: `OpenAPI openApi()`.

### KafkaTopicConfig
Espone due bean `NewTopic` (`topicProtocollazione`, `topicIndice`): Spring Kafka, via
AdminClient, crea i topic all'avvio se mancano. Comodo in locale; in produzione i
topic spesso li creano gli operatori.

### CacheConfig
`@EnableCaching` + un `CacheManager` Caffeine con la cache `"documenti"`
(`maximumSize(500)`, `expireAfterWrite(5 min)`). La costante `CACHE_DOCUMENTI` e
riusata nelle annotazioni del service.

### RestClientConfig
Crea il bean `RestClient` per chiamare il MS esterno, con `baseUrl` configurabile.

### AccreditamentoProperties
`@ConfigurationProperties(prefix = "app.accreditamento")`: mappa in modo type-safe
la lista `servizi` da `application.yml`. `serviziSicuri()` ritorna lista vuota se
assente (evita null). E il binding tipizzato della configurazione.

### DataSeeder
`CommandLineRunner`: all'avvio crea due utenti demo (`admin`, `mrossi`) se non
esistono, cifrando le password con il `PasswordEncoder`. Idempotente. E materiale
dimostrativo (in produzione gli utenti non si creano cosi).

---

## Web (controller e DTO)

### DTO (record in web/dto)
- **LoginRequest** `(username, password)` con `@NotBlank`.
- **RefreshRequest** `(refreshToken)` con `@NotBlank`.
- **TokenResponse** `(accessToken, refreshToken, tipo, nome)` + factory `bearer(...)`.
- **DocumentoRequest** `(titolo, contenuto)` con `@NotBlank`/`@Size` (validazione input).
- **DocumentoResponse**: vista esterna del documento + factory `da(Documento)`.
Sono `record` immutabili. Tenere i DTO separati dalle entita evita di esporre il
modello di persistenza e disaccoppia API e database.

### AuthController
**Cosa fa**: gestisce login, refresh e logout (rotte pubbliche).
- `login`: chiama `authenticationManager.authenticate(...)` (verifica le
  credenziali; se errate -> `BadCredentialsException` -> 401). Dal principal genera
  l'access token (`JwtService`) e crea il refresh token (`RefreshTokenService`).
  Ritorna `TokenResponse`.
- `refresh`: verifica il refresh token, ricarica l'utente, **ruota** il refresh
  token (revoca il vecchio, ne emette uno nuovo) e genera un nuovo access token.
- `logout`: revoca il refresh token (204 No Content). E idempotente.

### DocumentoController
**Cosa fa**: espone le operazioni sui documenti. Riceve l'utente con
`@AuthenticationPrincipal UtenteAutenticato`.
- `elenca(...)`: parametri `@RequestParam` opzionali (stato, proprietario, testo,
  creatoDa/creatoA con `@DateTimeFormat ISO`) + `Pageable`. Costruisce un
  `FiltroDocumenti` e mappa la pagina in DTO.
- `dettaglio(id)`: ritorna un documento (servito dalla cache del service).
- `scaricaPdf(id)`: ritorna `ResponseEntity<byte[]>` con `Content-Type:
  application/pdf` e `Content-Disposition: attachment`.
- `crea(...)`: `@ResponseStatus(CREATED)` + `@PreAuthorize("hasAnyRole('USER','ADMIN')")`.
- `aggiorna(...)`: `@PreAuthorize` per il ruolo; il controllo fine (proprietario)
  e nel service.

**Domanda**: *dove validi l'input?* Con `@Valid` sui DTO; gli errori diventano 400
nel `GlobalExceptionHandler`. *Perche due livelli di autorizzazione?* `@PreAuthorize`
filtra per ruolo (grana grossa); il service verifica il proprietario (grana fine).

### AnagraficaController
`GET /api/anagrafica/{username}`: delega al `AnagraficaClient`. Se l'utente non
esiste -> 404 (`RisorsaNonTrovataException`); se il servizio esterno e
irraggiungibile -> 502 (gestito globalmente).

### GlobalExceptionHandler
`@RestControllerAdvice`: cattura le eccezioni dei controller e le traduce in
`ProblemDetail` (RFC 7807). Mappatura: 404 (non trovata), 403 (accesso negato),
401 (credenziali/refresh), 400 (validazione/tipo parametro), 502 (servizio esterno),
500 (generico, con log dello stack ma senza esporlo). Centralizzare qui evita di
ripetere la gestione errori in ogni controller.

---

## Service

### Eccezioni applicative
- `RisorsaNonTrovataException` -> 404.
- `RefreshTokenNonValidoException` -> 401.
Sono `RuntimeException` (unchecked): non costringono a `try/catch` ovunque e
vengono tradotte centralmente in HTTP.

### RefreshTokenService
- `crea(username)`: genera un token `UUID` casuale, scadenza `now + giorni`, salva.
- `verifica(token)`: lo cerca; se manca o non e utilizzabile lancia
  `RefreshTokenNonValidoException`.
- `ruota(attuale)`: revoca il token corrente e ne crea uno nuovo per lo stesso
  utente. La **rotazione a ogni uso** riduce la finestra di rischio in caso di furto.
- `revoca(token)`: usato dal logout; idempotente (non fallisce se il token non c'e).

### DocumentoService (in dettaglio)
**Cosa fa**: cuore della logica di business. Dipendenze iniettate: repository
documenti e utenti, `OutboxService`, `DocumentStorage`, `DocumentoPdfService`,
`AccreditamentoProperties`.

`elenca(filtro, pageable)` (`readOnly`): delega a
`documentoRepository.findAll(DocumentoSpecifications.daFiltro(filtro), pageable)`.

`trova(id)`:
```java
@Transactional(readOnly = true)
@Cacheable(cacheNames = CACHE_DOCUMENTI, key = "#id")
public Documento trova(Long id) {
    return documentoRepository.findById(id)
        .orElseThrow(() -> new RisorsaNonTrovataException("...id " + id));
}
```
`@Cacheable`: alla prima chiamata interroga il DB e mette in cache; le successive
con lo stesso id non toccano il DB finche la voce non scade o non viene invalidata.

`crea(titolo, contenuto, utente)` (`@Transactional`):
1. costruisce il `Documento` con proprietario = username dell'utente;
2. `save` -> ottiene l'`id` generato;
3. assegna numero protocollo (`PRT-anno-id`) e stato `PROTOCOLLATO` (salvati al
   commit per dirty checking, l'entita e managed);
4. genera e salva il PDF (`generaESalvaPdf`), memorizza il riferimento;
5. registra l'evento nell'outbox (`registraEvento`).

`aggiorna(id, ...)` (`@Transactional` + `@CacheEvict key=#id`):
1. `trova(id)` (auto-invocazione: la cache non si applica, ottengo dati freschi);
2. `verificaPermessoModifica`: se non sei proprietario ne admin ->
   `AccessDeniedException` (403);
3. aggiorna titolo/contenuto, rigenera il PDF, registra l'evento;
4. `@CacheEvict` invalida la voce in cache di quel documento.

`scaricaPdf(id)` (`readOnly`): trova il documento; se non ha PDF -> 404; altrimenti
legge i byte dallo storage.

`applicaAggiornamentoIndice(evento)` (`@Transactional` + `@CacheEvict key=#evento.idDocumento()`):
cerca il documento; se c'e, applica l'eventuale nuovo stato, marca `indicizzato` e
imposta `dataIndicizzazione`; se non c'e, logga un warning (non lancia eccezioni,
per non bloccare il consumo del messaggio).

`generaESalvaPdf(documento)`: recupera il proprietario (`findByUsername`, con
fallback allo username se assente), costruisce `DatiAccreditamento` (incluso
`accreditamentoProperties.serviziSicuri()`), genera il PDF, lo salva con chiave
`documenti/<numeroProtocollo>.pdf` e ritorna il riferimento.

`registraEvento(tipo, documento)`: chiama `outboxService.registraProtocollazione(...)`
con un `ProtocollazioneEvent` (inclusa la chiave del PDF).

**Domande**: *come garantisci che PDF/protocollo siano coerenti col documento?*
Tutto avviene nella stessa transazione. *Cache e aggiornamenti come convivono?*
`@CacheEvict` su update/indice invalida la voce, evitando dati stantii.

---

## Messaging e Outbox

### ProtocollazioneEvent (record)
Evento in uscita: `idDocumento`, `titolo`, `numeroProtocollo`, `proprietario`,
`pdfRiferimento`, `operazione` (CREAZIONE/AGGIORNAMENTO), `timestamp`. Factory `di(...)`.

### IndiceAggiornamentoEvent (record)
Evento in ingresso dall'indice esterno: `idDocumento`, `nuovoStato` (opzionale),
`origine` (per tracciamento).

### IndiceConsumer
`@KafkaListener(topics = "${app.kafka.topic-indice}", groupId = "...")`: riceve gli
eventi gia deserializzati in `IndiceAggiornamentoEvent` (configurazione consumer in
`application.yml`) e delega a `documentoService.applicaAggiornamentoIndice(...)`.

### OutboxService (pattern Outbox - scrittura)
`registraProtocollazione(evento)` con `@Transactional(propagation = MANDATORY)`:
serializza l'evento in JSON e salva un `OutboxEvent`. `MANDATORY` impone che il
metodo giri **dentro la transazione del chiamante** (il service), cosi documento ed
evento si salvano in modo **atomico**. Risolve il "dual write problem".

### OutboxPublisher (pattern Outbox - invio)
```java
@Scheduled(fixedDelayString = "${app.outbox.polling-delay:5000}")
@Transactional
public void pubblicaInSospeso() {
    List<OutboxEvent> inSospeso = outboxRepository.findTop50ByPubblicatoFalseOrderByIdAsc();
    for (OutboxEvent e : inSospeso) {
        try {
            Object payload = deserializza(e);
            kafkaTemplate.send(e.getTopic(), e.getChiave(), payload).get(); // invio sincrono
            e.segnaPubblicato();   // marco solo se l'invio e confermato
        } catch (Exception ex) { /* resta da pubblicare, ritento dopo */ }
    }
}
```
Note: il metodo e transazionale, quindi i `segnaPubblicato()` (dirty checking)
vengono salvati al commit. L'invio sincrono (`.get()`) garantisce che marco
"pubblicato" solo dopo l'ack di Kafka. Se un invio fallisce, l'evento resta in
sospeso e verra ritentato (consegna **at-least-once**: i consumer devono essere
idempotenti). `deserializza` ricostruisce l'oggetto in base al `tipo`.

**Domanda**: *perche l'outbox e non pubblicare direttamente?* Perche commit DB e
invio Kafka non sono atomici: si rischia di salvare il dato e perdere l'evento (o
viceversa). L'outbox rende atomica la scrittura e affidabile l'invio.

---

## PDF

### DatiAccreditamento (record)
Modello dati che alimenta il template: `titolo`, `numeroProtocollo`, `nomeCompleto`,
`email`, `proprietario`, `dataCreazione`, `contenuto`, `servizi`. Tiene il
generatore di PDF disaccoppiato dalle entita JPA.

### DocumentoPdfService (in dettaglio)
**Cosa fa**: genera il PDF da un template XHTML con segnaposto.
- Nel costruttore carica una sola volta il template
  (`templates/documento-accreditamento.html`) in memoria.
- `genera(dati)`: riempie il template (`riempiTemplate`) e lo rende in PDF con
  Flying Saucer:
  ```java
  ITextRenderer renderer = new ITextRenderer();
  renderer.setDocumentFromString(html);
  renderer.layout();
  renderer.createPDF(output);
  ```
- `riempiTemplate`: sostituisce i `${...}` con i valori, **sempre dopo escaping**.
- `costruisciVociServizi`: trasforma la lista servizi in `<li>...</li>`.
- `escape`: sostituisce `& < > "` con le entita XML. **Perche e importante**: un
  valore con caratteri speciali (es. un titolo con `&`) romperebbe l'XHTML e quindi
  la generazione del PDF; inoltre previene injection nel documento.

Tenere il layout in un template separato (non nel codice Java) permette di
modificarne la grafica senza ricompilare.

---

## Storage

### DocumentStorage (interfaccia - Strategy)
Due metodi: `salva(chiave, contenuto, contentType)` e `leggi(chiave)`. Avere
un'interfaccia permette di cambiare backend (locale/S3) senza toccare la logica di
business. E il pattern **Strategy**.

### StorageException
Eccezione dedicata agli errori di storage (avvolge la causa originale).

### LocalFileSystemStorage (`@Profile("dev")`)
Salva i file su filesystem sotto una cartella base configurabile. `risolvi(chiave)`
normalizza il percorso e verifica che non "esca" dalla cartella base (protezione
da **path traversal**, es. `../../etc/passwd`).

### S3Config (`@Profile("prod")`)
Crea il bean `S3Client` (AWS SDK v2) con endpoint, regione, credenziali e
`forcePathStyle` configurabili. L'endpoint configurabile permette di usare lo stesso
codice con AWS S3 o con MinIO (S3-compatibile).

### S3ObjectStorage (`@Profile("prod")`)
Implementa `DocumentStorage` su S3. `@PostConstruct` crea il bucket se manca
(comodo con MinIO). `salva` -> `putObject`, `leggi` -> `getObjectAsBytes`.

**Domanda**: *come scegli l'implementazione?* Via `@Profile`: con `dev` e attivo il
bean locale, con `prod` quello S3. Il service dipende dall'interfaccia, non sa quale.

---

## Client REST esterni

### DatiAnagrafici (record)
DTO "pulito" restituito al nostro dominio: `username`, `nomeCompleto`, `email`,
`telefono`, `gruppi`. E il contratto stabile su cui si appoggia l'applicazione.

### AnagraficaClient (in dettaglio)
**Cosa fa**: chiama il MS esterno e ne mappa la risposta in modo resiliente.
- `recuperaPerUsername(username)`: chiama `GET /anagrafica/{username}` con
  `RestClient`, ottiene un `JsonNode`. Se 404 -> `Optional.empty()`; altri errori di
  rete (`RestClientException`) propagano (poi 502).
- `mappa(username, node)`: estrazione **difensiva**:
  ```java
  String nomeCompleto = primoNonVuoto(
      node.path("nomeCompleto").asText(""),
      unisci(node.path("nome").asText(""), node.path("cognome").asText("")),
      unisci(node.path("firstName").asText(""), node.path("lastName").asText("")),
      node.path("name").asText(""));
  ```
  `path()` ritorna un nodo "mancante" (non null) se il campo non c'e: niente NPE.
  Si provano piu nomi di campo alternativi (italiano/inglese, radice o annidato).
  Cosi, se il servizio esterno rinomina o sposta un campo, ci si adatta **in un solo
  punto** senza rompere il resto. E un piccolo **anti-corruption layer**.

**Domanda**: *come rendi il client robusto ai cambi del servizio esterno?* Leggo un
`JsonNode` con `path()` e default, mappando su un DTO mio stabile; i nomi alternativi
assorbono i rinominamenti.

---

## Common (filtri trasversali)

### RequestLoggingFilter
`@Order(HIGHEST_PRECEDENCE)`: gira per primo. Per ogni richiesta genera/propaga un
**id di correlazione** (header `X-Request-Id`), lo mette nell'MDC (cosi compare in
ogni riga di log della richiesta), e a fine richiesta logga metodo, URI, stato e
durata. Pulisce sempre l'MDC nel `finally` (il thread viene riusato). Estende
`OncePerRequestFilter` (Template Method).

### RateLimitingFilter (in dettaglio)
**Cosa fa**: limita le richieste per IP con algoritmo **token bucket**.
- Una `ConcurrentHashMap<String, Bucket>`: un secchiello per IP.
- `shouldNotFilter`: salta documentazione e health check.
- Per ogni richiesta: ricava l'IP (`X-Forwarded-For` o `remoteAddr`), prende/crea il
  bucket e prova a consumare un gettone. Se riesce prosegue, altrimenti risponde
  **429** con un piccolo JSON.
- `Bucket.provaAConsumare`: calcola la ricarica in modo **lazy** sul tempo trascorso
  (`gettoni += secondiTrascorsi * gettoniPerSecondo`, fino a `capacita`), poi
  consuma un gettone se disponibile. Metodo `synchronized` (accesso concorrente).

**Domanda**: *limite e robusto in cluster?* No: la mappa e in memoria, per piu
istanze servirebbe uno store condiviso (es. Redis). Per un singolo nodo va bene.

---

# Parte 3 - Flussi end-to-end

Qui i pezzi si ricuciono, seguendo una richiesta dall'inizio alla fine.

### A) Login e prima chiamata protetta
1. `POST /api/auth/login` arriva. Passa per `RequestLoggingFilter` (assegna
   requestId) e `RateLimitingFilter` (consuma un gettone). La rotta e pubblica.
2. `AuthController.login` chiama `authenticationManager.authenticate`: Spring
   Security usa `CustomUserDetailsService` per caricare l'utente e il
   `BCryptPasswordEncoder` per confrontare la password.
3. Se ok, `JwtService` crea l'access token e `RefreshTokenService` salva un refresh
   token. Il client riceve entrambi.
4. Per una chiamata protetta (es. `GET /api/documenti/1`) il client invia
   `Authorization: Bearer <accessToken>`. Il `JwtAuthenticationFilter` valida il
   token, ricarica l'utente, popola il SecurityContext. Il controller esegue,
   `@AuthenticationPrincipal` fornisce l'utente.

### B) Creazione di un documento
1. `POST /api/documenti` con JWT valido. `@PreAuthorize` verifica il ruolo.
2. `DocumentoService.crea` (transazione): salva il documento, assegna numero di
   protocollo e stato, genera il PDF (`DocumentoPdfService` riempie il template e
   Flying Saucer lo rende), lo carica sullo storage (locale o S3 a seconda del
   profilo), e registra l'evento nell'**outbox** (stessa transazione).
3. Commit unico: documento + riga outbox.
4. In background, ogni pochi secondi, `OutboxPublisher` invia l'evento a Kafka e lo
   marca pubblicato.

### C) Aggiornamento dall'indice esterno
1. Un sistema esterno pubblica un messaggio sul topic dell'indice.
2. `IndiceConsumer` lo riceve (gia deserializzato) e chiama
   `DocumentoService.applicaAggiornamentoIndice`: aggiorna stato e flag
   `indicizzato`, invalida la cache di quel documento.

### D) Refresh del token
1. `POST /api/auth/refresh` con il refresh token. `RefreshTokenService.verifica` lo
   valida; viene **ruotato** (vecchio revocato, nuovo emesso); `JwtService` genera
   un nuovo access token.

### E) Anagrafica esterna
1. `GET /api/anagrafica/{username}`. `AnagraficaClient` chiama il MS esterno, mappa
   il `JsonNode` in `DatiAnagrafici`. Senza il servizio reale -> 502 (atteso).

---

# Parte 4 - Domande di colloquio

Raccolta di domande probabili con risposte sintetiche.

**Architettura**
- *Com'e strutturata l'app?* A livelli: web -> service -> repository, con componenti
  dedicati per storage, messaging, PDF e client esterni. Dipendenze solo verso il basso.
- *Perche i DTO separati dalle entita?* Per non esporre il modello di persistenza e
  disaccoppiare API e DB.

**Spring / IoC**
- *Cos'e un bean e come si inietta?* Oggetto gestito dal container; si inietta via
  costruttore. Default singleton.
- *Constructor vs field injection?* Constructor: campi `final`, testabile, dipendenze
  esplicite. Preferita.

**Sicurezza**
- *Stateless con JWT: pro e contro?* Pro: scalabilita (nessuna sessione). Contro: la
  revoca dell'access token e difficile -> per questo c'e il refresh token revocabile.
- *Access vs refresh token?* Access breve e stateless (JWT); refresh lungo, opaco,
  persistito e revocabile, con rotazione a ogni uso.
- *Dove avviene l'autorizzazione?* Ruoli con `@PreAuthorize`; proprietario nel service.
- *Come sono salvate le password?* Hash BCrypt (con salt), mai in chiaro.
- *Perche CSRF disabilitato?* API stateless senza cookie di sessione.

**Persistenza**
- *Chi crea lo schema?* Flyway (migrazioni versionate); Hibernate solo `validate`.
- *Cos'e il dirty checking?* Hibernate rileva le modifiche alle entita managed e
  genera l'UPDATE al commit, senza `save` esplicito.
- *Lock ottimistico?* `@Version`: rileva modifiche concorrenti e lancia eccezione
  invece di sovrascrivere.
- *Query: quali stili usi?* Derivate dal nome, JPQL con `@Query`, Specification dinamiche.

**Messaging / Outbox**
- *Cos'e l'outbox e quale problema risolve?* Pubblicazione affidabile: scrive
  l'evento in DB nella stessa transazione del dato, un publisher lo invia poi a
  Kafka. Risolve il dual-write. Consegna at-least-once.
- *Producer e consumer qui?* Producer = eventi di protocollazione (via outbox);
  consumer = aggiornamenti dall'indice esterno.
- *Come gestisci un messaggio malformato?* `ErrorHandlingDeserializer`: lo scarta
  senza bloccare il consumo.

**Resilienza / integrazione**
- *Come chiami un altro microservizio?* Con `RestClient`, mappando il `JsonNode` in
  modo difensivo (path + nomi alternativi) su un DTO mio stabile.
- *Cosa succede se il servizio esterno e giu?* 502 gestito centralmente.

**Prestazioni**
- *Dove usi la cache e come la invalidi?* `@Cacheable` sulla lettura per id;
  `@CacheEvict` su aggiornamento e su evento dell'indice.
- *Rate limiting?* Token bucket per IP, 429 oltre soglia; in memoria (per cluster,
  Redis).

**Trasversali**
- *Come tracci una richiesta nei log?* Id di correlazione in MDC (`X-Request-Id`).
- *Come gestisci gli errori?* `@RestControllerAdvice` -> `ProblemDetail` (RFC 7807).
- *Profili dev/prod?* Cambiano lo storage (locale vs S3) tramite `@Profile`.

**Test**
- *Unit vs integrazione?* Unit con Mockito (veloci, senza Docker); integrazione con
  Testcontainers (PostgreSQL e Kafka reali), separati con Surefire/Failsafe.
- *Come testi un controller?* `@WebMvcTest` + MockMvc, con il service mockato.

**Pattern**
- *Quali pattern riconosci nel codice?* Strategy (storage), Adapter
  (`UtenteAutenticato`), Factory method, Builder, Template Method (filtri), Chain of
  Responsibility (filter chain), Repository, DTO, Specification, Transactional Outbox,
  DI/Singleton. Dettagli in [PATTERNS.md](PATTERNS.md).

---

*Fine della guida. Per i dettagli architetturali vedi [HLD.md](HLD.md) e
[LLD.md](LLD.md); per il codice, i sorgenti commentati in `src/`.*
