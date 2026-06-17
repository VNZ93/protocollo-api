# Guida completa al progetto Protocollo API

> Compendio di studio. Spiega il progetto dall'inizio alla fine: concetti di base,
> configurazione, e poi ogni classe con i suoi metodi e le righe piu importanti.
> Pensato per poterlo presentare a un colloquio senza esitazioni.

Per la visione d'insieme vedi [HLD.md](HLD.md) e [LLD.md](LLD.md); per i pattern
[PATTERNS.md](PATTERNS.md); per installare l'ambiente da zero e per la pipeline
CI [SETUP.md](SETUP.md).

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
- [Appendice A - Contesto, dominio e motivazioni](#appendice-a---contesto-dominio-e-motivazioni)
- [Appendice B - Glossario esteso](#appendice-b---glossario-esteso)
- [Appendice C - Mappa completa dei file](#appendice-c---mappa-completa-dei-file)
- [Appendice D - Le classi di test, una per una](#appendice-d---le-classi-di-test-una-per-una)
- [Appendice E - Riferimento API completo](#appendice-e---riferimento-api-completo)
- [Appendice F - Sicurezza approfondita](#appendice-f---sicurezza-approfondita)
- [Appendice G - Concorrenza, transazioni e consistenza](#appendice-g---concorrenza-transazioni-e-consistenza)
- [Appendice H - Configurazione completa e variabili d'ambiente](#appendice-h---configurazione-completa-e-variabili-dambiente)
- [Appendice I - Esecuzione, build e troubleshooting](#appendice-i---esecuzione-build-e-troubleshooting)
- [Appendice J - Domande di colloquio avanzate](#appendice-j---domande-di-colloquio-avanzate)
- [Appendice K - Errori comuni e anti-pattern evitati](#appendice-k---errori-comuni-e-anti-pattern-evitati)

---

# Parte 0 - Concetti di base

Questa parte spiega i concetti su cui poggia il progetto. Leggendola si capisce il
"perche" delle scelte fatte nel codice. Se conosci gia un argomento, saltalo.

### Cos'e un'applicazione backend / un'API REST
Il progetto e un **backend**: un programma senza interfaccia grafica che espone
delle funzioni via rete. Lo fa con un'**API REST**, cioe un insieme di URL
(endpoint) che si invocano via HTTP. Le convenzioni REST principali:
- si lavora su **risorse** (qui: documenti, profilo) identificate da un URL;
- il **metodo HTTP** indica l'azione: `GET` (leggi), `POST` (crea), `PUT`
  (aggiorna), `DELETE` (cancella);
- lo **stato** della risposta e un codice numerico (200 ok, 201 creato, 400 input
  errato, 401 non autenticato, 403 vietato, 404 non trovato, 409 conflitto, 429
  troppe richieste, 500 errore server, 502 servizio a monte non raggiungibile);
- corpo delle richieste/risposte in **JSON**.
REST e tendenzialmente **stateless**: ogni richiesta porta con se tutto cio che
serve (es. il token), il server non ricorda le richieste precedenti.

### Inversion of Control (IoC) e Dependency Injection (DI)
"Inversione del controllo" significa che non sei tu a creare e collegare gli
oggetti: lo fa un framework. In Spring un contenitore (l'`ApplicationContext`)
istanzia gli oggetti gestiti (i **bean**), risolve le loro dipendenze e gliele
"inietta". La **Dependency Injection** e il meccanismo con cui le dipendenze
arrivano dall'esterno, di norma tramite il **costruttore**.
- *Perche conviene?* Le classi non sono legate a implementazioni concrete: si
  testano facilmente (passo dei mock nei test) e si cambia un'implementazione senza
  modificare chi la usa.
- *Constructor injection vs field injection*: nel progetto si usa sempre il
  costruttore con campi `final`. Vantaggi: dipendenze esplicite e obbligatorie,
  oggetto immutabile, testabile senza il contenitore Spring.

### Bean, stereotipi e scope
Un **bean** e un oggetto creato e gestito da Spring. Le annotazioni di stereotipo
dicono a Spring di crearlo durante il **component scan**:
- `@Component`: generico.
- `@Service`: logica di business (semanticamente un componente).
- `@Repository`: accesso ai dati (aggiunge la traduzione delle eccezioni di persistenza).
- `@RestController`: gestisce richieste web e serializza la risposta in JSON.
- `@Configuration`: classe che definisce altri bean con metodi `@Bean`.
Lo **scope** di default e `singleton`: una sola istanza condivisa in tutta
l'applicazione. Per questo i bean devono essere **senza stato mutabile** (thread-safe):
piu richieste, su thread diversi, usano la stessa istanza contemporaneamente.

### Spring Boot e auto-configuration
Spring Boot riduce la configurazione manuale: in base alle librerie presenti nel
classpath configura automaticamente i componenti (se trova il driver PostgreSQL
crea il `DataSource`, se trova spring-kafka prepara producer/consumer, ecc.). Si
parte da `@SpringBootApplication` e si personalizza solo il necessario via
`application.yml`. Gli **starter** (es. `spring-boot-starter-web`) sono pacchetti di
dipendenze coerenti tra loro.

### Architettura a livelli (layered)
Il codice e diviso in livelli con responsabilita precise:
- **web** (controller, DTO): riceve le richieste, valida l'input, risponde.
- **service**: contiene la logica di business e le transazioni.
- **repository**: parla con il database.
Piu componenti di supporto (storage, messaging, client esterni). Regola: le
dipendenze vanno **solo verso il basso** (il web conosce il service, il service
conosce il repository, non viceversa). Questo rende il sistema comprensibile,
testabile e modificabile a pezzi.

### DTO (Data Transfer Object)
Un **DTO** e un oggetto usato solo per scambiare dati con l'esterno (input/output
delle API), distinto dall'**entita** (che mappa la tabella). Tenerli separati:
- evita di esporre dettagli interni del database;
- permette di far evolvere API e schema in modo indipendente;
- da il controllo su quali campi entrano/escono.
Qui i DTO sono `record` immutabili.

### JPA, Hibernate e il persistence context
**JPA** e lo standard Java per l'ORM (Object-Relational Mapping): mappare classi su
tabelle. **Hibernate** ne e l'implementazione usata qui. Concetti:
- **Entita** (`@Entity`): una classe mappata su una tabella.
- **Repository** (Spring Data): interfaccia che offre CRUD e query senza scrivere
  l'implementazione (la genera Spring).
- **Persistence context** (la "sessione"): durante una transazione tiene traccia
  delle entita caricate ("managed"). Se ne modifichi i campi, Hibernate se ne
  accorge e genera l'`UPDATE` al commit: e il **dirty checking** (non serve un
  `save` esplicito).
- **EAGER vs LAZY**: una relazione EAGER si carica subito con l'entita, una LAZY
  solo quando la si usa. Qui i ruoli dell'utente sono EAGER perche servono sempre.

### Transazioni e propagazione
`@Transactional` racchiude un blocco di operazioni: o riescono tutte (**commit**) o
nessuna (**rollback**, di norma su `RuntimeException`). Concetti usati nel progetto:
- `readOnly = true`: ottimizzazione per le sole letture.
- **Propagazione**: cosa succede se esiste gia una transazione. `REQUIRED`
  (default) si aggancia a quella esistente; `MANDATORY` **richiede** che ce ne sia
  gia una (la usa l'outbox per garantire l'atomicita con il salvataggio del dato).

### Lock ottimistico vs pessimistico
Con piu utenti che modificano lo stesso dato serve evitare sovrascritture silenziose.
- **Pessimistico**: blocco la riga (lock) finche non ho finito. Sicuro ma riduce la
  concorrenza.
- **Ottimistico**: non blocco; uso una colonna **versione** (`@Version`). Al salvataggio
  Hibernate controlla che la versione non sia cambiata; se e cambiata (qualcun altro
  ha modificato nel frattempo) lancia un'eccezione. E quello usato qui: piu adatto a
  un'app web dove i conflitti sono rari.

### Validazione (Bean Validation)
Le annotazioni come `@NotBlank`, `@Size` sui DTO dichiarano i vincoli sull'input;
con `@Valid` nel controller, Spring li applica e, se falliscono, solleva
un'eccezione tradotta in `400 Bad Request`. Validare al confine (nel web) tiene la
logica pulita dai controlli ripetitivi.

### Serializzazione JSON (Jackson)
Spring usa **Jackson** per convertire oggetti Java in JSON e viceversa. I `record` e
i POJO vengono serializzati automaticamente. Per il client esterno, invece di
mappare su una classe fissa, leggiamo un albero generico `JsonNode`: utile quando il
formato remoto puo cambiare.

### Sicurezza: autenticazione e autorizzazione
- **Autenticazione**: stabilire *chi sei* (login con credenziali).
- **Autorizzazione**: stabilire *cosa puoi fare* (ruoli, proprieta della risorsa).
- **Hashing password (BCrypt)**: le password non si salvano in chiaro ne cifrate in
  modo reversibile, ma come **hash** con **salt**. BCrypt e lento di proposito (cost
  factor), per rendere difficili gli attacchi a forza bruta.

### JWT (JSON Web Token)
Un JWT e una stringa composta da tre parti separate da punto: **header** (algoritmo),
**payload** (i **claim**: dati come utente, ruoli, scadenza) e **firma**. La firma
si calcola con una chiave segreta (qui **HMAC-SHA256**): chi riceve il token rifa il
calcolo e, se combacia, si fida del contenuto **senza interrogare il database**.
Abilita autenticazione **stateless**. Attenzione: il payload e solo codificato
(Base64), non cifrato: non vanno messi dati segreti.

### Access token e refresh token
- **Access token** (qui un JWT): breve durata, usato a ogni richiesta. Essendo
  stateless non si puo "revocare" prima della scadenza.
- **Refresh token**: lunga durata, usato solo per ottenere un nuovo access token.
  Qui e **opaco** (una stringa casuale) e **persistito** sul DB, quindi
  **revocabile**; viene **ruotato** a ogni uso (il vecchio si invalida).

### Messaggistica con Kafka
Kafka e una piattaforma di messaggi organizzata in **topic**. Un **producer**
pubblica messaggi su un topic, uno o piu **consumer** li leggono. Vantaggi:
disaccoppiamento (chi produce non conosce chi consuma) e resilienza. Concetti:
- **partizioni**: un topic e diviso in partizioni; la **chiave** del messaggio
  decide la partizione, garantendo l'ordine per quella chiave.
- **consumer group**: i consumer dello stesso gruppo si dividono le partizioni.
- **semantiche di consegna**: *at-most-once*, *at-least-once* (qui: l'evento puo
  arrivare piu volte, i consumer devono essere idempotenti), *exactly-once* (piu
  costosa).

### Idempotenza
Un'operazione e **idempotente** se ripeterla non cambia il risultato finale (es. il
logout che revoca un token gia revocato, o un consumer che applica due volte lo
stesso aggiornamento). E fondamentale con le consegne at-least-once.

### Cache
Conservare temporaneamente un risultato per non ricalcolarlo/rileggerlo. Qui una
cache **in memoria** (Caffeine) sulle letture per id, con scadenza e dimensione
massima, **invalidata** quando il dato cambia. Trade-off: rischio di dati "stantii"
se non si invalida correttamente; e locale alla singola istanza.

### Profili Spring
Permettono configurazioni e bean diversi per ambiente. Un bean con `@Profile("prod")`
esiste solo se il profilo `prod` e attivo. Qui distinguono lo storage: `dev`
(filesystem locale) e `prod` (S3/MinIO). Il profilo attivo si imposta con
`spring.profiles.active` o la variabile `SPRING_PROFILES_ACTIVE`.

### Pattern (panoramica)
Un "pattern" e una soluzione ricorrente e collaudata a un problema comune. Nel
progetto compaiono in modo naturale: Strategy, Adapter, Factory method, Builder,
Template Method, Chain of Responsibility, Repository, DTO, Specification, Outbox.
Sono dettagliati in [PATTERNS.md](PATTERNS.md).

---

# Parte 1 - Build e configurazione

### pom.xml (Maven) - dipendenza per dipendenza
Il `pom.xml` e il descrittore Maven del progetto. Punti salienti:

**Parent**: eredita da `spring-boot-starter-parent`. Porta con se il *dependency
management* (le versioni gia allineate e testate di centinaia di librerie): per
questo nei `<dependency>` quasi mai serve indicare la `<version>`. Imposta anche la
compilazione con `-parameters` (utile a Spring per leggere i nomi dei parametri,
es. nelle espressioni SpEL della cache) e Java 21.

**dependencyManagement**: importa il **BOM** dell'AWS SDK v2, cosi i suoi artifact
(`s3`, `apache-client`) condividono la stessa versione senza ripeterla.

**Dipendenze, gruppo per gruppo:**
- `spring-boot-starter-web`: MVC, server Tomcat embedded, Jackson per il JSON.
- `spring-boot-starter-validation`: Bean Validation (`@NotBlank`, `@Size`).
- `spring-boot-starter-data-jpa`: Hibernate + Spring Data (repository).
- `postgresql` (runtime): driver JDBC del database.
- `flyway-core` + `flyway-database-postgresql`: migrazioni; da Flyway 10 il supporto
  a PostgreSQL e in un modulo separato.
- `spring-boot-starter-security`: filter chain, autenticazione, autorizzazione.
- `jjwt-api` + `jjwt-impl` (runtime) + `jjwt-jackson` (runtime): creazione/validazione JWT.
- `spring-kafka`: producer e consumer Kafka.
- `spring-boot-starter-cache` + `caffeine`: astrazione cache + implementazione in memoria.
- `openpdf` + `flying-saucer-pdf-openpdf`: motore PDF + rendering da XHTML.
- `s3` + `apache-client` (AWS SDK v2): object storage S3/MinIO (usati in `prod`).
- `spring-boot-starter-actuator`: health/metrics/info.
- `springdoc-openapi-starter-webmvc-ui`: genera OpenAPI e serve Swagger UI.
- `spring-boot-starter-test`: JUnit 5, Mockito, AssertJ, MockMvc.
- `spring-security-test`: utility per testare la sicurezza (`user(...)`, `csrf()`).
- `spring-boot-testcontainers` + `testcontainers:junit-jupiter/postgresql/kafka`:
  avviano PostgreSQL e Kafka reali nei test di integrazione.

**build (plugin):**
- `spring-boot-maven-plugin`: produce il "fat jar" eseguibile e abilita `spring-boot:run`.
- `maven-surefire-plugin`: esegue gli **unit test** (`*Test`) nella fase `test`,
  escludendo `*IT`. Veloci, senza Docker.
- `maven-failsafe-plugin`: esegue i **test di integrazione** (`*IT`) nelle fasi
  `integration-test`/`verify`. Richiedono Docker (Testcontainers).

*Perche separare surefire e failsafe?* Per avere un ciclo veloce (`mvn test`)
durante lo sviluppo e riservare i test pesanti alla fase `verify`/CI.

### application.yml - chiave per chiave
- `spring.application.name`: nome logico dell'app (compare nei log/metriche).
- `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`: profilo attivo, `dev` di
  default, sovrascrivibile da variabile d'ambiente.
- `spring.datasource.url/username/password`: connessione PostgreSQL, con default per
  lo sviluppo locale e override via env (utile in Docker/Kubernetes).
- `spring.jpa.hibernate.ddl-auto: validate`: Hibernate **verifica** che le entita
  combacino con le tabelle, **senza modificarle** (lo schema lo gestisce Flyway).
- `spring.jpa.open-in-view: false`: chiude la sessione JPA al termine del service,
  niente query "a sorpresa" nel rendering della risposta (buona pratica).
- `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`: lavora in UTC, rende
  deterministico il mapping dei campi temporali.
- `spring.flyway.enabled/locations`: abilita le migrazioni in `classpath:db/migration`.
- `spring.kafka.bootstrap-servers`: indirizzo del broker.
- `spring.kafka.producer.*`: chiave `String`, valore serializzato in **JSON**;
  `spring.json.add.type.headers: false` per non legare il messaggio al tipo Java
  (consumer poliglotti).
- `spring.kafka.consumer.*`: `group-id`, `auto-offset-reset: earliest`, e
  `ErrorHandlingDeserializer` che delega a `JsonDeserializer` con tipo di default
  `IndiceAggiornamentoEvent` (un messaggio rotto viene scartato, non blocca il consumo).
- `app.jwt.secret/expiration-minutes/refresh-expiration-days`: chiave di firma e
  durate dei token.
- `app.kafka.topic-protocollazione/topic-indice`: nomi dei topic.
- `app.outbox.polling-delay`: intervallo del publisher dell'outbox.
- `app.rate-limit.enabled/capacity/refill-per-minute`: parametri del rate limiter.
- `app.accreditamento.servizi`: lista dei servizi mostrati nel PDF.
- `app.servizio-profilo.base-url`: URL del MS esterno.
- `management.endpoints.web.exposure.include`: espone `health,info,metrics`;
  `management.endpoint.health.show-details: always` mostra i dettagli di health.
- `logging.level`: `root: INFO`, `dev.protocollo: DEBUG`.

`application-dev.yml` definisce `app.storage.local.directory`; `application-prod.yml`
definisce `app.storage.s3.*`. La scelta dell'implementazione di storage avviene per
**profilo**, non per proprieta: e Spring a creare il bean giusto.

### logback-spring.xml
Configura il formato dei log. Importa i default di Spring Boot e definisce un
pattern che include `%X{requestId}`: l'id di correlazione messo nell'MDC dal
`RequestLoggingFilter`, indispensabile per seguire una singola richiesta tra tutte
le sue righe di log (es. filtrando su Kibana/ELK). In produzione si emetterebbe in
JSON con un encoder dedicato per facilitare l'indicizzazione.

### Migrazioni Flyway (db/migration)
File SQL con nome `V<numero>__<descrizione>.sql`, eseguiti **in ordine** e **una
sola volta**; Flyway registra cosa ha applicato nella tabella `flyway_schema_history`.
- **V1**: tabelle `utente`, `utente_ruolo`, `documento`.
- **V2**: documenti di esempio (cosi la GET restituisce subito qualcosa).
- **V3**: colonne `pdf_riferimento`, `indicizzato`, `data_indicizzazione` su `documento`.
- **V4**: tabella `refresh_token`.
- **V5**: colonna `email` su `utente`.
- **V6**: tabella `outbox_event`.

Regola d'oro: una migrazione gia applicata **non si modifica mai** (cambierebbe il
suo checksum e Flyway fallirebbe); per cambiare lo schema si aggiunge una nuova
migrazione. I tipi SQL sono scelti per combaciare con quanto si aspetta Hibernate in
`validate` (es. `TIMESTAMP(6) WITH TIME ZONE` per i campi `Instant`).

### Dockerfile e docker-compose
- **Dockerfile** (multi-stage):
  - *Stage build* (`maven:3.9-eclipse-temurin-21`): copia prima il solo `pom.xml` e
    fa `dependency:go-offline` (cosi il layer delle dipendenze resta in cache se il
    pom non cambia), poi copia `src` e fa `package -DskipTests`. I test non sono
    saltati "per pigrizia": girano nella pipeline CI (vedi [SETUP.md](SETUP.md)),
    cosi l'immagine non li ripete a ogni build e resta piu rapida da costruire.
  - *Stage runtime* (`eclipse-temurin:21-jre`): immagine leggera con la sola JRE,
    esegue il JAR come **utente non privilegiato** (buona pratica di sicurezza).
- **docker-compose.yml**: avvia PostgreSQL, Kafka (modalita KRaft, senza ZooKeeper),
  Kafka UI (porta 8081) e MinIO (porte 9000/9001). Con `--profile app` avvia anche
  l'applicazione (in profilo Spring `prod`, collegata a Postgres/Kafka/MinIO via nome
  di servizio). Le porte sono mappate su `localhost` per l'accesso da host.

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
**Cosa fa**: elenca i ruoli applicativi.
```java
public enum Ruolo { USER, ADMIN }
```
- Un `enum` Java e un tipo con un numero fisso di valori costanti: piu sicuro di una
  stringa (il compilatore impedisce valori non previsti).
- Convenzione di Spring Security: l'**authority** di un ruolo si scrive con il
  prefisso `ROLE_` (`ROLE_USER`, `ROLE_ADMIN`). Il prefisso lo aggiungiamo noi in
  `UtenteAutenticato`, cosi nell'enum restano i nomi "puri".
- Perche un enum e non una tabella ruoli? Per semplicita: i ruoli qui sono pochi e
  fissi. Se dovessero diventare dinamici (gestiti a runtime), servirebbe una tabella.

### StatoDocumento (enum)
**Cosa fa**: rappresenta il ciclo di vita di un documento.
```java
public enum StatoDocumento { BOZZA, PROTOCOLLATO, ARCHIVIATO }
```
- `BOZZA`: appena creato (stato iniziale di default).
- `PROTOCOLLATO`: ha ricevuto un numero di protocollo.
- `ARCHIVIATO`: non piu operativo (vi si arriva, ad esempio, da un evento dell'indice).
- Nell'entita `Documento` e salvato come **stringa** (`@Enumerated(STRING)`), quindi
  nel DB si legge `PROTOCOLLATO` e non un numero: piu leggibile e robusto ai
  riordini dell'enum.

### Documento (entita)
**Cosa fa**: rappresenta un documento da protocollare; mappa la tabella `documento`.

Annotazioni di classe:
```java
@Entity
@Table(name = "documento")
public class Documento { ... }
```
- `@Entity`: dice a JPA che questa classe e un'entita persistente.
- `@Table(name = "documento")`: nome esplicito della tabella (altrimenti userebbe il
  nome della classe).

Campi e relative annotazioni:
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```
- `@Id`: chiave primaria. `@GeneratedValue(IDENTITY)`: la genera il database (colonna
  identity), quindi l'`id` e disponibile solo **dopo** il primo `save`.
```java
@Column(nullable = false, length = 200)
private String titolo;
@Column(columnDefinition = "text")
private String contenuto;
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private StatoDocumento stato;
```
- `@Column(nullable=false, length=200)`: vincoli mappati su `VARCHAR(200) NOT NULL`.
- `columnDefinition = "text"`: per contenuti lunghi usa il tipo `TEXT`.
- `@Enumerated(STRING)`: salva il **nome** dell'enum (non l'ordinale numerico).
- Altri campi: `numeroProtocollo` (valorizzato alla protocollazione), `proprietario`
  (username, usato per i permessi), `pdfRiferimento` (chiave del PDF), `indicizzato`
  + `dataIndicizzazione` (aggiornati dal consumer), `dataCreazione`/`dataAggiornamento`
  (audit).
```java
@Version
private Long version;
```
- `@Version`: abilita il **lock ottimistico**. Hibernate include `version` nella
  `WHERE` degli UPDATE e la incrementa; se due transazioni modificano la stessa riga,
  la seconda fallisce con `OptimisticLockException` invece di sovrascrivere in silenzio.

Callback del ciclo di vita:
```java
@PrePersist void prePersist() { /* imposta date e stato di default */ }
@PreUpdate  void preUpdate()  { /* aggiorna dataAggiornamento */ }
```
- `@PrePersist`: eseguita da Hibernate prima dell'INSERT.
- `@PreUpdate`: prima di ogni UPDATE. Cosi le date di audit si gestiscono nell'entita,
  senza ripeterle nel service.

Il costruttore vuoto `protected Documento()` e **richiesto da Hibernate**, che
istanzia l'entita via reflection; e `protected` per scoraggiarne l'uso nel codice
applicativo (che usa il costruttore con i parametri). Alcuni campi (id, date,
version) hanno solo il getter: sono gestiti dal framework, non vanno impostati a mano.

**Domande**: *perche EnumType.STRING e non ORDINAL?* Perche con ORDINAL si salva un
numero (0,1,2): riordinare o inserire un valore nell'enum corromperebbe i dati. *Quando
e disponibile l'id?* Solo dopo il `save` (strategia IDENTITY). *Cos'e il lock
ottimistico?* Controllo di concorrenza via `@Version` (vedi sopra).

### Utente (entita)
**Cosa fa**: mappa la tabella `utente`. Campi: `username` (`unique = true`),
`password` (hash BCrypt, mai in chiaro), `nomeCompleto`, `email`, `attivo`, `ruoli`.

Mapping dei ruoli:
```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "utente_ruolo", joinColumns = @JoinColumn(name = "utente_id"))
@Column(name = "ruolo", nullable = false, length = 20)
@Enumerated(EnumType.STRING)
private Set<Ruolo> ruoli = new HashSet<>();
```
- `@ElementCollection`: una collezione di valori (non entita) salvata in una tabella a
  parte, `utente_ruolo`, legata da `utente_id`.
- `fetch = EAGER`: i ruoli si caricano **subito** con l'utente, perche servono a ogni
  autenticazione per costruire le authority (con LAZY rischierei un errore se la
  sessione e gia chiusa).
- `Set<Ruolo>`: niente duplicati.

Note:
- `attivo` permette di **disabilitare** un utente senza cancellarlo (soft-disable):
  il filtro JWT, ricaricando l'utente, vede `isEnabled()=false` e lo blocca.
- `email` ha **solo il setter**: e valorizzata dopo la costruzione (nel `DataSeeder`),
  per non cambiare il costruttore esistente e non rompere i test.
- `password` ha solo il getter (la si imposta nel costruttore): non si modifica a giro.

### RefreshToken (entita)
**Cosa fa**: token opaco persistito (tabella `refresh_token`).
Campi: `token` (stringa casuale **unica**), `username`, `scadenza` (`Instant`),
`revocato` (boolean).
```java
public boolean isUtilizzabile() {
    return !revocato && scadenza.isAfter(Instant.now());
}
```
- `isUtilizzabile()`: vero solo se non revocato **e** non scaduto. Incapsula la regola
  nell'entita (invece di sparpagliarla nel service).
- `setRevocato(true)`: usato da logout e rotazione.
**Perche persistito e non un JWT?** Perche un JWT stateless non si puo invalidare
prima della scadenza; salvandolo su DB possiamo **revocarlo** (logout) e **ruotarlo**.

### OutboxEvent (entita)
**Cosa fa**: una riga della tabella outbox = un evento da pubblicare.
Campi: `tipo` (es. `"PROTOCOLLAZIONE"`, serve a sapere in quale classe deserializzare
il payload), `aggregateId` (a quale entita si riferisce, es. id documento), `topic`,
`chiave` (chiave Kafka, per il partizionamento), `payload` (l'evento serializzato in
JSON, colonna `TEXT`), `creatoIl`, `pubblicato`, `pubblicatoIl`.
```java
@PrePersist void prePersist() { this.creatoIl = Instant.now(); }
public void segnaPubblicato() { this.pubblicato = true; this.pubblicatoIl = Instant.now(); }
```
- `@PrePersist`: imposta `creatoIl` all'inserimento.
- `segnaPubblicato()`: chiamato dal publisher dopo l'invio confermato; essendo
  l'entita managed, il cambiamento viene salvato al commit (dirty checking).

---

## Repository

### UtenteRepository
```java
public interface UtenteRepository extends JpaRepository<Utente, Long> {
    Optional<Utente> findByUsername(String username);
}
```
- `extends JpaRepository<Utente, Long>`: l'interfaccia eredita gratis i metodi CRUD
  (`save`, `findById`, `findAll`, `delete`, ...). `Utente` e il tipo, `Long` il tipo
  dell'id. Non scriviamo l'implementazione: la genera Spring Data a runtime.
- `findByUsername`: **query derivata** dal nome del metodo. Spring interpreta
  `findBy` + `Username` e genera `SELECT ... WHERE username = ?`.
- Ritorna `Optional<Utente>`: niente `null`, si gestisce esplicitamente l'assenza.

### DocumentoRepository
```java
public interface DocumentoRepository
        extends JpaRepository<Documento, Long>, JpaSpecificationExecutor<Documento> {

    long countByProprietario(String proprietario);
    Page<Documento> findByStato(StatoDocumento stato, Pageable pageable);

    @Query("""
            select d from Documento d
            where lower(d.titolo) like lower(concat('%', :testo, '%'))
            """)
    Page<Documento> cercaPerTitolo(@Param("testo") String testo, Pageable pageable);
}
```
Mostra **quattro** stili di interrogazione:
- CRUD ereditato da `JpaRepository`;
- **query derivate** dal nome: `countByProprietario`, `findByStato` (con `Pageable`
  per la paginazione);
- **JPQL** esplicita con `@Query`: `cercaPerTitolo` cerca (case-insensitive) un testo
  nel titolo. JPQL lavora su entita/campi (`Documento d`, `d.titolo`), non su tabelle;
  `:testo` e un parametro nominato, legato con `@Param`;
- **Specification** dinamiche grazie a `JpaSpecificationExecutor` (vedi sotto).

### FiltroDocumenti (record)
```java
public record FiltroDocumenti(StatoDocumento stato, String proprietario,
                              String testo, Instant creatoDa, Instant creatoA) {}
```
- Semplice contenitore immutabile dei criteri di ricerca, **tutti opzionali** (un
  campo `null` = filtro non applicato).
- Un `record` genera in automatico costruttore, getter (`stato()`, ...),
  `equals/hashCode/toString`: ideale per un oggetto di soli dati.
- Perche un record e non tanti parametri sparsi? Per passare "il filtro" come una cosa
  sola tra controller, service e Specification, in modo leggibile ed estendibile.

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
**Cosa fa**: adatta la nostra entita `Utente` all'interfaccia `UserDetails` di Spring
Security. E l'oggetto che finisce nel SecurityContext e che i controller ricevono con
`@AuthenticationPrincipal`. E il pattern **Adapter**.

Costruzione delle authority:
```java
this.authorities = utente.getRuoli().stream()
        .map(ruolo -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + ruolo.name()))
        .toList();
```
- Converte ogni `Ruolo` in una `GrantedAuthority` aggiungendo il prefisso `ROLE_`
  (`ROLE_USER`, `ROLE_ADMIN`): e cio che `hasRole(...)`/`hasAnyRole(...)` si aspetta.
```java
public boolean isAmministratore() {
    return authorities.contains(new SimpleGrantedAuthority("ROLE_" + Ruolo.ADMIN.name()));
}
@Override public boolean isEnabled() { return attivo; }
```
- `isAmministratore()`: comodita per il controllo fine dei permessi nel service.
- Implementa i metodi di `UserDetails`: `getAuthorities`, `getPassword`,
  `getUsername`, e i flag di stato. `isEnabled()` ritorna `attivo` (un utente non
  attivo viene rifiutato); gli altri flag (account non scaduto/bloccato) sono `true`,
  non gestiti in questo esempio.

### CustomUserDetailsService
**Cosa fa**: implementa `UserDetailsService`, il punto in cui Spring Security recupera
l'utente.
```java
@Override
public UserDetails loadUserByUsername(String username) {
    return utenteRepository.findByUsername(username)
            .map(UtenteAutenticato::new)
            .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + username));
}
```
- Cerca l'utente nel DB; se c'e lo avvolge in `UtenteAutenticato` (`map`), altrimenti
  lancia `UsernameNotFoundException`.
- Spring Security lo usa in **due** momenti: al **login** (per ottenere l'hash della
  password da confrontare) e nel **filtro JWT** (per ricaricare l'utente a ogni
  richiesta, cosi un utente disabilitato viene bloccato anche con token valido).

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
**Cosa fa**: configura la documentazione OpenAPI e abilita il login col token in
Swagger. Bean `OpenAPI openApi()`:
```java
.addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
.components(new Components().addSecuritySchemes("bearer-jwt",
    new SecurityScheme().type(HTTP).scheme("bearer").bearerFormat("JWT")));
```
- Dichiara uno **schema di sicurezza** "bearer JWT" e lo applica di default a tutte le
  operazioni: in Swagger UI compare il pulsante **Authorize** per incollare il token e
  provare gli endpoint protetti dal browser.

### KafkaTopicConfig
**Cosa fa**: dichiara i topic Kafka.
```java
@Bean NewTopic topicProtocollazione(@Value("${app.kafka.topic-protocollazione}") String n) {
    return TopicBuilder.name(n).partitions(1).replicas(1).build();
}
```
- Esponendo un bean `NewTopic`, Spring Kafka (via AdminClient) **crea il topic
  all'avvio se manca**. Qui due topic: protocollazione (uscita) e indice (ingresso).
- `partitions(1).replicas(1)`: valori da sviluppo locale; in produzione si dimensionano
  e spesso i topic li creano gli operatori, non l'app. `TopicBuilder` e un **Builder**.

### CacheConfig
**Cosa fa**: abilita e configura la cache.
```java
@Configuration @EnableCaching
public class CacheConfig {
    public static final String CACHE_DOCUMENTI = "documenti";
    @Bean CacheManager cacheManager() {
        CaffeineCacheManager m = new CaffeineCacheManager(CACHE_DOCUMENTI);
        m.setCaffeine(Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(5)));
        return m;
    }
}
```
- `@EnableCaching`: attiva l'elaborazione di `@Cacheable`/`@CacheEvict`.
- Caffeine in memoria con tetto di 500 voci e scadenza 5 minuti dalla scrittura.
- La costante `CACHE_DOCUMENTI` evita "stringhe magiche": e riusata nelle annotazioni
  del `DocumentoService`.

### RestClientConfig
**Cosa fa**: crea il `RestClient` per il MS esterno.
```java
@Bean RestClient profiloRestClient(RestClient.Builder builder,
        @Value("${app.servizio-profilo.base-url}") String baseUrl) {
    return builder.baseUrl(baseUrl).build();
}
```
- Parte dal `RestClient.Builder` gia configurato da Spring Boot e fissa l'URL di base
  (configurabile): cosi si punta ad ambienti diversi senza toccare il codice del client.

### AccreditamentoProperties
**Cosa fa**: binding tipizzato della configurazione.
```java
@ConfigurationProperties(prefix = "app.accreditamento")
public record AccreditamentoProperties(List<String> servizi) {
    public List<String> serviziSicuri() { return servizi != null ? servizi : List.of(); }
}
```
- Mappa la sezione `app.accreditamento` di `application.yml` in un oggetto immutabile
  (registrato grazie a `@ConfigurationPropertiesScan` sul main).
- `serviziSicuri()` restituisce lista vuota se la proprieta manca: evita `null` a valle.
- Vantaggio rispetto a tanti `@Value`: tutto in un punto, tipizzato e testabile.

### DataSeeder
**Cosa fa**: popola utenti demo al primo avvio.
```java
@Override public void run(String... args) {
    creaSeNonEsiste("admin", "admin123", "Amministratore di sistema", "admin@example.com",
            Set.of(Ruolo.ADMIN, Ruolo.USER));
    creaSeNonEsiste("mrossi", "password123", "Mario Rossi", "mario.rossi@example.com",
            Set.of(Ruolo.USER));
}
```
- Implementa `CommandLineRunner`: il metodo `run` parte dopo l'avvio del contesto.
- `creaSeNonEsiste` salta se l'utente esiste gia (**idempotente**) e cifra la password
  con il `PasswordEncoder` (mai in chiaro nel DB). L'email si imposta col setter.
- E materiale **dimostrativo**: in un sistema reale gli utenti non si creano con
  password nel codice.

---

## Web (controller e DTO)

### DTO (record in web/dto)
Sono `record` immutabili: oggetti di solo trasporto dati. Tenerli separati dalle
entita evita di esporre il modello di persistenza e disaccoppia API e database.
```java
public record LoginRequest(
        @NotBlank(message = "Lo username e obbligatorio") String username,
        @NotBlank(message = "La password e obbligatoria") String password) {}
```
- `@NotBlank`: vincolo di Bean Validation; con `@Valid` nel controller, un campo vuoto
  diventa `400` con il messaggio indicato (gestito dal `GlobalExceptionHandler`).
```java
public record TokenResponse(String accessToken, String refreshToken, String tipo, String nome) {
    public static TokenResponse bearer(String accessToken, String refreshToken, String nome) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", nome);
    }
}
```
- **Factory method** `bearer(...)`: costruisce l'oggetto fissando `tipo = "Bearer"`,
  piu leggibile del costruttore grezzo. Usato da login e refresh.
- **RefreshRequest** `(refreshToken)` con `@NotBlank`: corpo di refresh e logout.
- **DocumentoRequest** `(titolo, contenuto)` con `@NotBlank` e `@Size(max=...)`:
  valida l'input di POST/PUT.
```java
public static DocumentoResponse da(Documento d) {
    return new DocumentoResponse(d.getId(), d.getTitolo(), ..., d.getDataAggiornamento());
}
```
- **DocumentoResponse**: vista esterna del documento. Il factory `da(Documento)`
  converte l'entita in DTO in un punto solo (riuso nei controller).

### AuthController
**Cosa fa**: gestisce login, refresh e logout (rotte pubbliche).

login:
```java
Authentication autenticazione = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(richiesta.username(), richiesta.password()));
UtenteAutenticato utente = (UtenteAutenticato) autenticazione.getPrincipal();
String accessToken = jwtService.generaToken(utente);
String refreshToken = refreshTokenService.crea(utente.getUsername()).getToken();
return TokenResponse.bearer(accessToken, refreshToken, utente.getNomeCompleto());
```
- Delega la verifica delle credenziali a Spring Security (`authenticate`): se sono
  errate lancia `BadCredentialsException` -> 401 (non lo gestiamo qui, ci pensa l'advice).
- Dal `principal` (il nostro `UtenteAutenticato`) genera l'**access token** e crea il
  **refresh token**, restituendoli al client.

refresh:
```java
RefreshToken corrente = refreshTokenService.verifica(richiesta.refreshToken());
UtenteAutenticato utente =
        (UtenteAutenticato) userDetailsService.loadUserByUsername(corrente.getUsername());
RefreshToken nuovo = refreshTokenService.ruota(corrente);
String accessToken = jwtService.generaToken(utente);
return TokenResponse.bearer(accessToken, nuovo.getToken(), utente.getNomeCompleto());
```
- Verifica il refresh token, **ricarica l'utente** dal DB (cosi i ruoli nel nuovo
  access token sono aggiornati), **ruota** il refresh token (vecchio revocato, nuovo
  emesso) e genera un nuovo access token.

logout:
```java
@ResponseStatus(HttpStatus.NO_CONTENT)
public void logout(@Valid @RequestBody RefreshRequest richiesta) {
    refreshTokenService.revoca(richiesta.refreshToken());
}
```
- Revoca il refresh token e risponde **204 No Content**. E **idempotente**: revocare
  un token gia revocato/inesistente non genera errore.

### DocumentoController
**Cosa fa**: espone le operazioni sui documenti. Riceve l'utente con
`@AuthenticationPrincipal UtenteAutenticato`.

elenca (paginazione + filtri):
```java
@GetMapping
public Page<DocumentoResponse> elenca(
        @RequestParam(required = false) StatoDocumento stato,
        @RequestParam(required = false) String proprietario,
        @RequestParam(required = false) String testo,
        @RequestParam(required = false) @DateTimeFormat(iso = DATE_TIME) Instant creatoDa,
        @RequestParam(required = false) @DateTimeFormat(iso = DATE_TIME) Instant creatoA,
        Pageable pageable) {
    FiltroDocumenti filtro = new FiltroDocumenti(stato, proprietario, testo, creatoDa, creatoA);
    return documentoService.elenca(filtro, pageable).map(DocumentoResponse::da);
}
```
- `@RequestParam(required = false)`: parametri di query opzionali; `@DateTimeFormat`
  converte le date ISO in `Instant`; `Pageable` arriva da `page/size/sort`
  (es. `?page=0&size=10&sort=dataCreazione,desc`).
- `.map(DocumentoResponse::da)`: trasforma la `Page<Documento>` in `Page<DocumentoResponse>`.

scaricaPdf:
```java
@GetMapping("/{id}/pdf")
public ResponseEntity<byte[]> scaricaPdf(@PathVariable Long id) {
    byte[] pdf = documentoService.scaricaPdf(id);
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documento-" + id + ".pdf\"")
            .body(pdf);
}
```
- Ritorna i byte con `Content-Type: application/pdf` e `Content-Disposition: attachment`
  (il browser lo scarica come file).

Altre operazioni:
- `dettaglio(id)`: ritorna un documento (servito dalla cache del service).
- `crea(...)`: `@ResponseStatus(CREATED)` (201) + `@PreAuthorize("hasAnyRole('USER','ADMIN')")`.
- `aggiorna(...)`: `@PreAuthorize` per il ruolo; il controllo fine (proprietario) e nel service.

**Domanda**: *dove validi l'input?* Con `@Valid` sui DTO; gli errori diventano 400 nel
`GlobalExceptionHandler`. *Perche due livelli di autorizzazione?* `@PreAuthorize` filtra
per ruolo (grana grossa); il service verifica il proprietario (grana fine).

### ProfiloController
```java
@GetMapping("/{username}")
public DatiProfilo dettaglio(@PathVariable String username) {
    return profiloClient.recuperaPerUsername(username)
            .orElseThrow(() -> new RisorsaNonTrovataException("Profilo non trovato per " + username));
}
```
- Delega al `ProfiloClient`, che ritorna un `Optional`. Se vuoto (utente non
  trovato) -> `RisorsaNonTrovataException` -> 404. Se il servizio esterno e
  irraggiungibile, il client lancia `RestClientException` -> 502 (gestito globalmente).
- Controller volutamente sottile: tutta la logica di chiamata/mappatura sta nel client.

### GlobalExceptionHandler
**Cosa fa**: gestione centralizzata degli errori per tutti i controller.
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RisorsaNonTrovataException.class)
    public ProblemDetail gestisciNonTrovata(RisorsaNonTrovataException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public ProblemDetail gestisciGenerico(Exception ex) {
        log.error("Errore non gestito", ex);   // stack solo nei log
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Si e verificato un errore interno");   // messaggio neutro al client
    }
}
```
- `@RestControllerAdvice`: intercetta le eccezioni sollevate dai controller.
- `@ExceptionHandler(...)`: un metodo per tipo di eccezione; ritorna un `ProblemDetail`
  (formato standard RFC 7807, `application/problem+json`).
- Mappatura completa: 404 (`RisorsaNonTrovataException`), 403 (`AccessDeniedException`),
  401 (`BadCredentialsException`, `RefreshTokenNonValidoException`), 400
  (`MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`), 502
  (`RestClientException`), 500 (qualsiasi altra: si **logga lo stack** ma non lo si
  espone, per non rivelare dettagli interni).
- Centralizzare qui evita `try/catch` ripetuti in ogni controller e da risposte di
  errore coerenti.

---

## Service

### Eccezioni applicative
```java
public class RisorsaNonTrovataException extends RuntimeException { ... }     // -> 404
public class RefreshTokenNonValidoException extends RuntimeException { ... }  // -> 401
```
- Estendono `RuntimeException` (**unchecked**): non obbligano a `try/catch` ovunque, e
  causano il **rollback** della transazione se sollevate dentro un metodo `@Transactional`.
- Sono tradotte in codici HTTP nel `GlobalExceptionHandler`. Eccezioni "di dominio"
  dedicate rendono il codice piu leggibile di codici di ritorno o `null`.

### RefreshTokenService
**Cosa fa**: gestisce il ciclo di vita dei refresh token.
```java
@Transactional
public RefreshToken crea(String username) {
    RefreshToken token = new RefreshToken(UUID.randomUUID().toString(), username,
            Instant.now().plus(durata));
    return refreshTokenRepository.save(token);
}
@Transactional(readOnly = true)
public RefreshToken verifica(String token) {
    RefreshToken rt = refreshTokenRepository.findByToken(token)
            .orElseThrow(() -> new RefreshTokenNonValidoException("Refresh token non valido"));
    if (!rt.isUtilizzabile()) throw new RefreshTokenNonValidoException("Refresh token scaduto o revocato");
    return rt;
}
@Transactional
public RefreshToken ruota(RefreshToken attuale) {
    attuale.setRevocato(true);
    refreshTokenRepository.save(attuale);
    return crea(attuale.getUsername());
}
@Transactional
public void revoca(String token) {
    refreshTokenRepository.findByToken(token).ifPresent(rt -> { rt.setRevocato(true); refreshTokenRepository.save(rt); });
}
```
- `crea`: token = `UUID` casuale (opaco), scadenza `now + durata` (giorni da config), salva.
- `verifica`: se manca o non e `isUtilizzabile()` (scaduto/revocato) lancia 401.
- `ruota`: revoca quello attuale e ne crea uno nuovo per lo stesso utente. La
  **rotazione a ogni refresh** limita la finestra di rischio: un token rubato e usato
  una volta dall'attaccante diventa subito inutilizzabile per la vittima (e viceversa).
- `revoca`: usato dal logout; `ifPresent` lo rende **idempotente** (nessun errore se il
  token non esiste).

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
**Cosa fa**: evento in **uscita** pubblicato quando un documento e creato/aggiornato.
```java
public record ProtocollazioneEvent(Long idDocumento, String titolo, String numeroProtocollo,
        String proprietario, String pdfRiferimento, TipoOperazione operazione, Instant timestamp) {
    public enum TipoOperazione { CREAZIONE, AGGIORNAMENTO }
    public static ProtocollazioneEvent di(TipoOperazione op, Long id, String titolo,
            String numero, String proprietario, String pdfRif) {
        return new ProtocollazioneEvent(id, titolo, numero, proprietario, pdfRif, op, Instant.now());
    }
}
```
- `record` immutabile, serializzato in JSON da Jackson per Kafka.
- L'enum annidato `TipoOperazione` distingue creazione e aggiornamento.
- Factory `di(...)`: imposta il `timestamp` corrente, costruzione in un punto solo.

### IndiceAggiornamentoEvent (record)
**Cosa fa**: evento in **ingresso** dall'indice esterno.
```java
public record IndiceAggiornamentoEvent(Long idDocumento, StatoDocumento nuovoStato, String origine) {}
```
- `nuovoStato` opzionale (puo essere `null`: in tal caso non si cambia stato);
  `origine` identifica il sistema mittente (utile nei log/tracciamento).

### IndiceConsumer
```java
@KafkaListener(topics = "${app.kafka.topic-indice}", groupId = "${spring.kafka.consumer.group-id}")
public void consuma(IndiceAggiornamentoEvent evento) {
    documentoService.applicaAggiornamentoIndice(evento);
}
```
- `@KafkaListener`: registra un consumer sul topic dell'indice. Spring deserializza il
  messaggio direttamente in `IndiceAggiornamentoEvent` (tipo di default configurato in
  `application.yml`).
- Il consumer e **sottile**: delega la logica al service. Cosi la stessa logica e
  testabile a unita senza Kafka.

### OutboxService (pattern Outbox - scrittura)
```java
@Transactional(propagation = Propagation.MANDATORY)
public void registraProtocollazione(ProtocollazioneEvent evento) {
    String payload = objectMapper.writeValueAsString(evento);
    String aggregateId = String.valueOf(evento.idDocumento());
    outboxRepository.save(new OutboxEvent(
            TIPO_PROTOCOLLAZIONE, aggregateId, topicProtocollazione, aggregateId, payload));
}
```
- Serializza l'evento in JSON (`ObjectMapper`) e salva una riga `OutboxEvent`.
- `Propagation.MANDATORY`: **pretende** di girare dentro una transazione gia aperta
  (quella del `DocumentoService`). Cosi la riga outbox e il documento vengono salvati
  nello **stesso commit** (atomicita). Se non ci fosse una transazione, lancerebbe
  eccezione: e una garanzia esplicita, non un'assunzione.
- Risolve il **dual write problem**: niente piu "dato salvato ma evento perso".

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
**Cosa fa**: modello immutabile che alimenta il template PDF.
```java
public record DatiAccreditamento(String titolo, String numeroProtocollo, String nomeCompleto,
        String email, String proprietario, Instant dataCreazione, String contenuto, List<String> servizi) {}
```
- Mette insieme dati che vengono da fonti diverse: il **documento** (titolo, numero,
  contenuto, data), l'**utente** proprietario (nome, email) e la **configurazione**
  (servizi). Il `DocumentoService` lo compone, il `DocumentoPdfService` lo consuma.
- Cosi il generatore di PDF **non conosce** le entita JPA ne i repository: riceve solo
  i dati che gli servono (basso accoppiamento, facile da testare).

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
```java
public interface DocumentStorage {
    String salva(String chiave, byte[] contenuto, String contentType);
    byte[] leggi(String chiave);
}
```
- Due sole operazioni: salva e leggi per chiave. E il contratto del pattern
  **Strategy**: il `DocumentoService` dipende da questa interfaccia, non
  dall'implementazione concreta, quindi cambiare backend non tocca la logica.

### StorageException
```java
public class StorageException extends RuntimeException {
    public StorageException(String messaggio, Throwable causa) { super(messaggio, causa); }
}
```
- Eccezione dedicata agli errori di storage: avvolge la causa originale (`IOException`,
  `S3Exception`) in un tipo del nostro dominio, cosi il resto del codice non dipende
  dalle eccezioni specifiche della tecnologia.

### LocalFileSystemStorage (`@Profile("dev")`)
**Cosa fa**: salva i file su filesystem sotto una cartella base configurabile.
```java
public String salva(String chiave, byte[] contenuto, String contentType) {
    Path destinazione = risolvi(chiave);
    Files.createDirectories(destinazione.getParent());
    Files.write(destinazione, contenuto);
    return chiave;
}
private Path risolvi(String chiave) {
    Path risolto = cartellaBase.resolve(chiave).normalize();
    if (!risolto.startsWith(cartellaBase)) throw new StorageException("Chiave non valida: " + chiave, null);
    return risolto;
}
```
- `@Profile("dev")`: attivo solo nel profilo di sviluppo.
- `risolvi`: combina la chiave con la cartella base, **normalizza** il percorso e
  verifica che resti **dentro** la cartella base. E una difesa da **path traversal**
  (es. una chiave `../../etc/passwd` verrebbe rifiutata).
- `contentType` qui non serve (lo accetta per rispettare l'interfaccia comune a S3).

### S3Config (`@Profile("prod")`)
```java
@Bean S3Client s3Client(...) {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .forcePathStyle(pathStyle)
        .build();
}
```
- Crea il client S3 dell'AWS SDK v2 (un **Builder**). `endpointOverride` configurabile:
  lo stesso codice funziona con AWS S3 o con **MinIO** (S3-compatibile).
- `forcePathStyle(true)`: necessario per MinIO (bucket nel path, non come sottodominio).

### S3ObjectStorage (`@Profile("prod")`)
```java
@PostConstruct void creaBucketSeMancante() {
    try { s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build()); }
    catch (NoSuchBucketException e) { s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build()); }
}
public String salva(String chiave, byte[] contenuto, String contentType) {
    s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(chiave).contentType(contentType).build(),
            RequestBody.fromBytes(contenuto));
    return chiave;
}
public byte[] leggi(String chiave) {
    return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(chiave).build()).asByteArray();
}
```
- `@PostConstruct`: alla partenza crea il bucket se manca (comodo con MinIO; su AWS di
  norma esiste gia).
- `salva` -> `putObject`; `leggi` -> `getObjectAsBytes`. La **chiave** del nostro
  storage diventa la *object key* dentro il bucket.

**Domanda**: *come scegli l'implementazione?* Via `@Profile`: con `dev` Spring crea il
bean locale, con `prod` quello S3. Il service dipende dall'interfaccia e non sa quale
sia attiva: e il bello dello Strategy + DI.

---

## Client REST esterni

### DatiProfilo (record)
```java
public record DatiProfilo(String username, String nomeCompleto, String email,
        String telefono, List<String> gruppi) {}
```
- DTO "pulito" restituito al nostro dominio, indipendente dal formato JSON remoto.
- E il **contratto stabile** su cui si appoggia il resto dell'applicazione: anche se il
  servizio esterno cambia struttura, qui non cambia nulla (si adatta solo il client).

### ProfiloClient (in dettaglio)
**Cosa fa**: chiama il MS esterno e ne mappa la risposta in modo resiliente.
- `recuperaPerUsername(username)`: chiama `GET /profilo/{username}` con
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
**Cosa fa**: assegna un id di correlazione e logga ogni richiesta.
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    protected void doFilterInternal(req, res, chain) {
        String requestId = ricavaRequestId(req);     // header X-Request-Id o UUID nuovo
        MDC.put("requestId", requestId);
        res.setHeader("X-Request-Id", requestId);
        long inizio = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long durata = System.currentTimeMillis() - inizio;
            log.info("{} {} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), res.getStatus(), durata);
            MDC.remove("requestId");
        }
    }
}
```
- `@Order(HIGHEST_PRECEDENCE)`: gira **per primo**, cosi il `requestId` e disponibile a
  tutti i filtri e log successivi.
- **MDC** (Mapped Diagnostic Context): una mappa per-thread che Logback inserisce in
  ogni riga di log (pattern `%X{requestId}`). Permette di **correlare** tutte le righe
  di una singola richiesta.
- L'id si **propaga** se il client invia `X-Request-Id` (utile per tracciare attraverso
  piu microservizi), altrimenti se ne genera uno nuovo.
- Il `finally` logga esito e durata e **pulisce sempre l'MDC**: cruciale perche il
  thread viene riusato per altre richieste (altrimenti "perderebbe" l'id sbagliato).
- Estende `OncePerRequestFilter` (Template Method): implemento solo `doFilterInternal`.

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

### E) Profilo esterno
1. `GET /api/profilo/{username}`. `ProfiloClient` chiama il MS esterno, mappa
   il `JsonNode` in `DatiProfilo`. Senza il servizio reale -> 502 (atteso).

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

# Appendice A - Contesto, dominio e motivazioni

### Di cosa parla il progetto
Il dominio e la **protocollazione di documenti**. Protocollare un documento
significa registrarlo assegnandogli un identificativo univoco e progressivo (il
**numero di protocollo**), tracciandone data e responsabile. E un'operazione tipica
della pubblica amministrazione e di molti gestionali documentali.

In questo progetto un documento ha un titolo, un contenuto, un proprietario, uno
stato e, dopo la protocollazione, un numero e una versione PDF. Alla creazione viene
generato un **PDF di accreditamento** che riepiloga i dati dell'utente e i servizi a
cui risulta abilitato.

### Perche esiste questo progetto
E un progetto **dimostrativo da portfolio**: l'obiettivo non e la completezza
funzionale, ma mostrare in piccolo un backend realistico che tocca i temi che si
incontrano davvero in produzione: sicurezza, persistenza, messaggistica, storage di
file, integrazione tra microservizi, osservabilita e test. E pensato per essere
letto, studiato e spiegato.

### Confini del sistema (cosa fa e cosa NON fa)
- **Fa**: autenticazione/autorizzazione, CRUD parziale dei documenti (create, read,
  update; non c'e delete by design), generazione PDF, eventi su Kafka, consumo di
  aggiornamenti, chiamata a un servizio esterno.
- **Non fa**: gestione utenti via API (gli utenti demo sono creati al boot),
  cancellazione documenti, UI grafica, multi-tenancy, rate limiting distribuito.

### Decisioni di dominio
- Il **proprietario** del documento e memorizzato come username (stringa), non come
  foreign key verso `utente`: il documento resta valido e leggibile anche se
  il profilo utente cambia o viene riorganizzata. E una scelta consapevole
  (accoppiamento debole), con il trade-off di non avere integrita referenziale su
  quel campo.
- Il **numero di protocollo** ha formato `PRT-<anno>-<id a 6 cifre>`: semplice e
  leggibile. In un sistema reale la numerazione progressiva annuale richiederebbe
  attenzione alla concorrenza (sequenze dedicate).
- La creazione **protocolla subito** il documento (stato `PROTOCOLLATO`): scelta per
  semplicita didattica, cosi POST e PUT generano entrambi un evento.

---

# Appendice B - Glossario esteso

| Termine | Significato nel progetto |
|---------|--------------------------|
| Protocollazione | Registrazione di un documento con numero univoco. |
| Numero di protocollo | Identificativo `PRT-anno-id` assegnato alla protocollazione. |
| Entita | Classe Java mappata su una tabella (`@Entity`). |
| DTO | Oggetto per lo scambio dati con l'esterno, separato dall'entita. |
| Repository | Interfaccia di accesso ai dati (Spring Data). |
| Bean | Oggetto gestito dal contenitore Spring. |
| IoC / DI | Inversione del controllo / iniezione delle dipendenze. |
| Persistence context | "Sessione" JPA che traccia le entita managed. |
| Dirty checking | Hibernate genera l'UPDATE rilevando le modifiche alle entita. |
| Lock ottimistico | Controllo di concorrenza via colonna `version` (`@Version`). |
| Transazione | Blocco di operazioni atomico (commit/rollback). |
| Propagazione | Comportamento transazionale rispetto a una transazione esistente. |
| Principal | L'utente autenticato nel SecurityContext (`UtenteAutenticato`). |
| Authority / ruolo | Permesso dell'utente; i ruoli sono authority con prefisso `ROLE_`. |
| JWT | Token firmato con i claim dell'utente. |
| Claim | Singola informazione dentro un JWT (sub, ruoli, exp...). |
| Access token | JWT di breve durata per autorizzare le richieste. |
| Refresh token | Token opaco, lungo e revocabile, per rinnovare l'access token. |
| Rotazione token | Sostituzione del refresh token a ogni uso. |
| BCrypt | Algoritmo di hashing delle password (lento, con salt). |
| HMAC-SHA256 | Algoritmo di firma simmetrica usato per i JWT. |
| Producer / Consumer | Chi pubblica / chi legge messaggi Kafka. |
| Topic / Partizione | Canale Kafka / suddivisione che preserva l'ordine per chiave. |
| Outbox | Tabella di appoggio per pubblicare eventi in modo affidabile. |
| At-least-once | Consegna in cui un messaggio puo arrivare piu volte. |
| Idempotenza | Ripetere un'operazione non cambia il risultato. |
| Specification | Criterio di query componibile (Spring Data JPA). |
| Strategy | Pattern: comportamenti intercambiabili dietro un'interfaccia. |
| Adapter | Pattern: adatta un'interfaccia a un'altra attesa. |
| Template Method | Pattern: scheletro fisso, passi variabili nei sottotipi. |
| MDC | Mapped Diagnostic Context: dati per-thread nei log (es. requestId). |
| ProblemDetail | Formato standard (RFC 7807) per gli errori HTTP. |
| Profilo | Configurazione/bean per ambiente (`dev`, `prod`). |
| Testcontainers | Libreria che avvia container Docker reali nei test. |
| MockMvc | Strumento per testare i controller senza un vero server. |

---

# Appendice C - Mappa completa dei file

Riferimento rapido: ogni file sorgente con la sua responsabilita in una riga.

**Avvio e configurazione**
| File | Responsabilita |
|------|----------------|
| `ProtocolloApplication` | Avvio; abilita scheduling, config-properties, page-DTO. |
| `config/SecurityConfig` | Filter chain di sicurezza, encoder, auth manager. |
| `config/OpenApiConfig` | Metadati OpenAPI e schema "bearer JWT" per Swagger. |
| `config/KafkaTopicConfig` | Crea i topic Kafka all'avvio. |
| `config/CacheConfig` | Abilita la cache e configura Caffeine. |
| `config/RestClientConfig` | Crea il `RestClient` per il MS esterno. |
| `config/AccreditamentoProperties` | Binding tipizzato di `app.accreditamento`. |
| `config/DataSeeder` | Crea gli utenti demo al primo avvio. |

**Dominio**
| File | Responsabilita |
|------|----------------|
| `domain/Documento` | Entita documento. |
| `domain/Utente` | Entita utente. |
| `domain/RefreshToken` | Entita refresh token. |
| `domain/OutboxEvent` | Entita riga outbox. |
| `domain/Ruolo` | Enum ruoli. |
| `domain/StatoDocumento` | Enum stati del documento. |

**Persistenza**
| File | Responsabilita |
|------|----------------|
| `repository/DocumentoRepository` | CRUD + query + Specification sui documenti. |
| `repository/UtenteRepository` | Accesso utenti (`findByUsername`). |
| `repository/RefreshTokenRepository` | Accesso refresh token. |
| `repository/OutboxEventRepository` | Accesso eventi outbox. |
| `repository/FiltroDocumenti` | Record con i criteri di ricerca. |
| `repository/DocumentoSpecifications` | Costruzione dinamica delle query. |

**Sicurezza**
| File | Responsabilita |
|------|----------------|
| `security/JwtService` | Genera/valida i JWT. |
| `security/JwtAuthenticationFilter` | Valida il token a ogni richiesta. |
| `security/CustomUserDetailsService` | Carica l'utente dal DB. |
| `security/UtenteAutenticato` | Adatta `Utente` a `UserDetails` (principal). |

**Web**
| File | Responsabilita |
|------|----------------|
| `web/AuthController` | Login, refresh, logout. |
| `web/DocumentoController` | CRUD documenti + download PDF. |
| `web/ProfiloController` | Dati di profilo dal MS esterno. |
| `web/GlobalExceptionHandler` | Mappa le eccezioni in risposte HTTP. |
| `web/dto/*` | Record di richiesta/risposta. |

**Service**
| File | Responsabilita |
|------|----------------|
| `service/DocumentoService` | Logica di business dei documenti. |
| `service/RefreshTokenService` | Ciclo di vita dei refresh token. |
| `service/RisorsaNonTrovataException` | Eccezione -> 404. |
| `service/RefreshTokenNonValidoException` | Eccezione -> 401. |

**Messaging, PDF, Storage, Client, Common**
| File | Responsabilita |
|------|----------------|
| `messaging/ProtocollazioneEvent` | Evento in uscita. |
| `messaging/IndiceAggiornamentoEvent` | Evento in ingresso. |
| `messaging/IndiceConsumer` | Consumer Kafka degli aggiornamenti. |
| `messaging/outbox/OutboxService` | Scrive l'evento in outbox. |
| `messaging/outbox/OutboxPublisher` | Invia gli eventi outbox a Kafka. |
| `pdf/DocumentoPdfService` | Genera il PDF dal template. |
| `pdf/DatiAccreditamento` | Dati per il template PDF. |
| `storage/DocumentStorage` | Interfaccia storage (Strategy). |
| `storage/LocalFileSystemStorage` | Storage su filesystem (dev). |
| `storage/S3ObjectStorage` | Storage su S3/MinIO (prod). |
| `storage/S3Config` | Bean `S3Client` (prod). |
| `storage/StorageException` | Errore di storage. |
| `client/ProfiloClient` | Chiama il MS esterno, mappa il JSON. |
| `client/DatiProfilo` | DTO profilo. |
| `common/logging/RequestLoggingFilter` | Id di correlazione + log richieste. |
| `common/ratelimit/RateLimitingFilter` | Rate limiting per IP. |

---

# Appendice D - Le classi di test, una per una

La strategia distingue test **unitari** (veloci, isolati, senza Docker) e di
**integrazione** (`*IT`, con Testcontainers). Vedi anche Parte 1 (Surefire/Failsafe).

### JwtServiceTest (unitario)
Verifica il `JwtService` istanziandolo direttamente (niente Spring). Casi:
- un token generato e valido e da esso si riestrae lo username;
- un token manomesso (firma alterata) e riconosciuto non valido;
- un token firmato con un'altra chiave non e valido.
Insegna: come testare logica pura senza contesto; perche la firma e cio che rende il
token sicuro.

### DocumentoServiceTest (unitario, Mockito)
Testa la logica di business con tutte le dipendenze **mockate** (repository, outbox,
storage, PDF) e un `AccreditamentoProperties` reale. Costruisce il service a mano nel
`@BeforeEach`. Casi:
- la creazione assegna numero/stato, genera il PDF e registra l'evento (verificato
  con un `ArgumentCaptor` sull'evento di tipo `CREAZIONE`);
- il proprietario puo aggiornare il proprio documento;
- un amministratore puo aggiornare documenti altrui;
- un utente non proprietario riceve `AccessDeniedException` e **non** registra eventi;
- aggiornare un documento inesistente lancia `RisorsaNonTrovataException`;
- l'aggiornamento dall'indice allinea stato e flag `indicizzato`;
- un aggiornamento dall'indice per documento inesistente non lancia eccezioni.
Tecniche: `when/thenReturn`, `thenAnswer` (per simulare l'id generato dal DB),
`verify`, `ArgumentCaptor`, `ReflectionTestUtils.setField` (per impostare l'`id` su
un'entita senza setter). Nota: Mockito qui e in modalita *strict stubs*, quindi gli
stub di PDF/storage si mettono solo nei test che li usano.

### DocumentoControllerTest (slice web, MockMvc)
`@WebMvcTest(DocumentoController.class)`: carica solo il livello web; il
`DocumentoService` e `@MockBean`. Poiche `@WebMvcTest` istanzia anche i bean
`Filter`, si forniscono `@MockBean` per `JwtService` e `CustomUserDetailsService`
(dipendenze del `JwtAuthenticationFilter`). L'utente si simula con il post-processor
`user(principal)` di Spring Security Test. Casi: GET dettaglio (200 + JSON), POST
creazione (201), POST senza titolo (400 per validazione). Insegna: come testare
routing, serializzazione e validazione senza database ne logica reale.

### DocumentoIntegrationIT (integrazione, Testcontainers)
`@SpringBootTest(webEnvironment = RANDOM_PORT)` con PostgreSQL e Kafka avviati da
Testcontainers e collegati con `@ServiceConnection`. Usa `TestRestTemplate` (chiamate
HTTP reali). Casi:
- senza token la lista documenti risponde 401;
- flusso completo: login -> POST (con generazione PDF) -> PUT -> download PDF
  (verifica anche la firma `%PDF` dei byte);
- il refresh token rilascia un nuovo access token (diverso dal precedente) e con
  quello si accede a una risorsa protetta.
Insegna: come verificare l'intero stack end-to-end con infrastruttura reale ma
effimera (i container nascono e muoiono con il test). Gira in profilo `dev`, quindi
i PDF vanno sullo storage locale (cartella temporanea).

---

# Appendice E - Riferimento API completo

Tutte le rotte tranne `/api/auth/**`, Swagger e `/actuator/health` richiedono
`Authorization: Bearer <accessToken>`. Errori in formato `ProblemDetail`.

### POST /api/auth/login
Richiesta:
```json
{ "username": "admin", "password": "admin123" }
```
Risposta 200:
```json
{ "accessToken": "eyJ...", "refreshToken": "0b5f...", "tipo": "Bearer", "nome": "Amministratore di sistema" }
```
Errori: 400 (campi mancanti), 401 (credenziali errate).

### POST /api/auth/refresh
Richiesta: `{ "refreshToken": "..." }`. Risposta 200: nuova coppia di token (il
refresh restituito e **diverso**, per la rotazione). Errori: 401 (token mancante,
scaduto o revocato).

### POST /api/auth/logout
Richiesta: `{ "refreshToken": "..." }`. Risposta: **204 No Content**. Idempotente.

### GET /api/documenti
Query string (tutti opzionali): `stato` (BOZZA|PROTOCOLLATO|ARCHIVIATO),
`proprietario`, `testo` (cerca nel titolo), `creatoDa`/`creatoA` (date ISO),
`page`, `size`, `sort` (es. `dataCreazione,desc`).
Esempio: `GET /api/documenti?stato=PROTOCOLLATO&testo=determina&page=0&size=10`.
Risposta 200: pagina (contenuto + metadati di paginazione). Errori: 400 (valore di
`stato` non valido), 401.

### GET /api/documenti/{id}
Risposta 200: il documento. Errori: 401, 404.

### GET /api/documenti/{id}/pdf
Risposta 200: corpo binario PDF (`Content-Type: application/pdf`,
`Content-Disposition: attachment`). Errori: 401, 404 (documento o PDF assente).

### POST /api/documenti
Permessi: ruolo USER o ADMIN. Richiesta:
```json
{ "titolo": "Determina X", "contenuto": "..." }
```
Risposta 201: il documento creato (con numero protocollo, stato PROTOCOLLATO,
riferimento PDF). Errori: 400 (titolo vuoto/troppo lungo), 401, 403.

### PUT /api/documenti/{id}
Permessi: proprietario o ADMIN (verifica nel service). Stesso body della POST.
Risposta 200: documento aggiornato. Errori: 400, 401, 403, 404.

### GET /api/profilo/{username}
Risposta 200: `{ username, nomeCompleto, email, telefono, gruppi }`. Errori: 401,
404 (non trovato), 502 (MS esterno irraggiungibile - atteso senza servizio reale).

### Esempio di errore (ProblemDetail)
```json
{ "type": "about:blank", "title": "Not Found", "status": 404,
  "detail": "Documento non trovato con id 99" }
```

---

# Appendice F - Sicurezza approfondita

### Modello delle minacce (sintetico) e contromisure
| Minaccia | Contromisura nel progetto |
|----------|---------------------------|
| Furto credenziali / password deboli | Hash BCrypt con salt; password mai in chiaro/log. |
| Replay di un token rubato | Access token a breve scadenza; refresh revocabile e ruotato. |
| Manomissione del token | Firma HMAC-SHA256 verificata a ogni richiesta. |
| Forza bruta sul login | Rate limiting per IP (429). |
| Escalation di privilegi | Autorizzazione per ruolo + controllo proprietario nel service. |
| Path traversal sui file | Normalizzazione e controllo della cartella base nello storage locale. |
| Injection nel PDF | Escaping XML dei valori nel template. |
| Leak di dettagli interni | Errori generici verso il client; stack solo nei log. |
| Utente disabilitato con token valido | Ricarico l'utente dal DB nel filtro (intercetto `attivo=false`). |

### Struttura di un JWT
Tre parti Base64Url separate da punto: `header.payload.signature`.
- **header**: `{ "alg": "HS256", "typ": "JWT" }`.
- **payload (claim)**: `sub` (username), `ruoli`, `nome`, `iat` (emesso il),
  `exp` (scade il).
- **signature**: `HMACSHA256(base64(header) + "." + base64(payload), secret)`.
Il payload e **leggibile** (solo codificato, non cifrato): per questo non contiene
dati segreti. La sicurezza sta nella firma: senza il `secret` non si puo produrre un
token valido ne modificarne il contenuto.

### Perche BCrypt e non SHA-256 "liscio"
Gli hash veloci (SHA-256) sono inadatti alle password: un attaccante puo provarne
miliardi al secondo. BCrypt e **lento di proposito** (cost factor configurabile) e
incorpora un **salt** casuale per ogni password, neutralizzando le rainbow table.
`BCryptPasswordEncoder.matches(raw, hash)` confronta senza mai decifrare (l'hash non
e reversibile).

### Catena dei filtri (ordine)
1. `RequestLoggingFilter` (precedenza massima): assegna il `requestId`.
2. `RateLimitingFilter`: applica il limite per IP.
3. Catena di Spring Security, che include il `JwtAuthenticationFilter` (inserito
   prima del filtro username/password). Qui si popola il SecurityContext.
4. Il controller, con l'autorizzazione di metodo (`@PreAuthorize`).
Nota: il `JwtAuthenticationFilter` e disabilitato come filtro servlet globale (via
`FilterRegistrationBean` con `setEnabled(false)`) per non eseguirlo due volte.

### Autorizzazione a due livelli
- **Grana grossa** (ruolo): `@PreAuthorize("hasAnyRole('USER','ADMIN')")` sui metodi.
- **Grana fine** (proprieta): nel service, `verificaPermessoModifica` consente la
  modifica solo al proprietario o a un ADMIN. La logica fine sta nel service perche
  e li che si conoscono i dati (chi e il proprietario del documento).

---

# Appendice G - Concorrenza, transazioni e consistenza

### Confini transazionali
I metodi che scrivono in `DocumentoService` (`crea`, `aggiorna`,
`applicaAggiornamentoIndice`) sono `@Transactional`: tutto quel che fanno (salvare il
documento, scrivere la riga outbox) avviene in un **unico commit**. Le letture sono
`@Transactional(readOnly = true)`. Questo garantisce che dato di business ed evento
siano coerenti: o si salvano entrambi o nessuno.

### Atomicita dato + evento (outbox)
`OutboxService.registraProtocollazione` e `@Transactional(propagation = MANDATORY)`:
**pretende** di essere chiamato dentro la transazione del service. Cosi la riga
outbox viene scritta nello stesso commit del documento. Se la transazione fallisce,
non resta ne il documento ne l'evento. L'invio a Kafka avviene dopo, separatamente,
dal publisher.

### Self-invocation (un tranello classico)
In `aggiorna` si chiama `this.trova(id)`. Essendo una **chiamata interna** allo
stesso bean, **non passa dal proxy** di Spring: quindi le annotazioni di `trova`
(`@Cacheable`, `@Transactional`) non si applicano in quella chiamata. Qui va bene
anzi e voluto: in aggiornamento vogliamo il dato fresco dal DB, non dalla cache. E
un punto su cui i colloqui insistono: *le annotazioni AOP funzionano solo sulle
chiamate che passano dal proxy*.

### Lock ottimistico in pratica
La colonna `version` (`@Version`) viene controllata da Hibernate negli UPDATE:
`UPDATE documento SET ... , version = version + 1 WHERE id = ? AND version = ?`. Se
nessuna riga viene aggiornata (perche la versione e cambiata), Hibernate lancia
`OptimisticLockException`. E adatto a contesti con conflitti rari (tipica app web).

### Cache e coerenza
La cache `documenti` puo contenere una copia di un documento. Ogni scrittura che lo
modifica (`aggiorna`, `applicaAggiornamentoIndice`) ha `@CacheEvict` sulla chiave id:
la voce viene rimossa, cosi la lettura successiva ripopola dal DB. Senza `@CacheEvict`
si servirebbero dati stantii. La cache e in memoria e **per-istanza**: con piu nodi,
ognuno ha la sua (per coerenza globale servirebbe una cache distribuita).

### Consegna at-least-once e idempotenza
Il publisher dell'outbox invia e poi marca "pubblicato": se l'app cade tra l'invio e
il commit del flag, al riavvio reinvia -> il messaggio puo arrivare **due volte**. E
una scelta consapevole (at-least-once, piu semplice di exactly-once). La contromisura
e l'**idempotenza** lato consumer: applicare due volte lo stesso aggiornamento
dell'indice porta allo stesso stato finale.

### Thread-safety dei bean
I bean sono singleton e condivisi tra thread: per questo non hanno stato mutabile
(solo dipendenze `final`). L'eccezione gestita con cura e il `RateLimitingFilter`,
che ha stato condiviso (la mappa dei secchielli): usa `ConcurrentHashMap` e un metodo
`synchronized` sul singolo bucket per essere corretto sotto carico concorrente.

---

# Appendice H - Configurazione completa e variabili d'ambiente

Quasi ogni valore ha un default per lo sviluppo ed e sovrascrivibile da variabile
d'ambiente (forma `${VAR:default}`): comodo per Docker/Kubernetes, dove la config si
passa dall'esterno e i segreti non si versionano.

| Variabile d'ambiente | Proprieta | Default | Note |
|----------------------|-----------|---------|------|
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `dev` | `prod` per S3/MinIO. |
| `DB_URL` | `spring.datasource.url` | jdbc:postgresql://localhost:5432/protocollo | |
| `DB_USERNAME` | `spring.datasource.username` | protocollo | |
| `DB_PASSWORD` | `spring.datasource.password` | protocollo | In prod: segreto. |
| `KAFKA_BOOTSTRAP` | `spring.kafka.bootstrap-servers` | localhost:9092 | |
| `JWT_SECRET` | `app.jwt.secret` | (placeholder dev) | **Da cambiare in prod**, >= 32 caratteri. |
| `JWT_EXPIRATION_MINUTES` | `app.jwt.expiration-minutes` | 60 | Durata access token. |
| `JWT_REFRESH_EXPIRATION_DAYS` | `app.jwt.refresh-expiration-days` | 7 | Durata refresh token. |
| `KAFKA_TOPIC` | `app.kafka.topic-protocollazione` | protocollo.documenti.protocollazione | |
| `KAFKA_TOPIC_INDICE` | `app.kafka.topic-indice` | protocollo.indice.aggiornamenti | |
| `OUTBOX_POLLING_DELAY` | `app.outbox.polling-delay` | 5000 | ms tra un giro e l'altro. |
| `RATE_LIMIT_ENABLED` | `app.rate-limit.enabled` | true | |
| `RATE_LIMIT_CAPACITY` | `app.rate-limit.capacity` | 100 | Gettoni max per IP. |
| `RATE_LIMIT_REFILL` | `app.rate-limit.refill-per-minute` | 100 | Ricarica/minuto. |
| `PROFILO_BASE_URL` | `app.servizio-profilo.base-url` | http://localhost:9099 | MS esterno. |
| `STORAGE_LOCAL_DIR` | `app.storage.local.directory` | tmp/protocollo-storage | Profilo dev. |
| `S3_ENDPOINT` | `app.storage.s3.endpoint` | http://localhost:9000 | Profilo prod. |
| `S3_REGION` | `app.storage.s3.region` | us-east-1 | Profilo prod. |
| `S3_ACCESS_KEY` | `app.storage.s3.access-key` | minioadmin | Profilo prod. |
| `S3_SECRET_KEY` | `app.storage.s3.secret-key` | minioadmin | Profilo prod. |
| `S3_BUCKET` | `app.storage.s3.bucket` | protocollo | Profilo prod. |
| `S3_PATH_STYLE` | `app.storage.s3.path-style` | true | Necessario per MinIO. |

**Profili.** `dev` (default) usa lo storage su filesystem; `prod` usa S3/MinIO. La
selezione del bean di storage avviene con `@Profile`, non con un `if`: e Spring a
creare solo l'implementazione del profilo attivo.

---

# Appendice I - Esecuzione, build e troubleshooting

> Avvio rapido, comandi Maven e installazione degli strumenti da zero sono
> documentati una sola volta per evitare versioni disallineate: vedi
> [Avvio rapido nel README](../README.md#avvio-rapido) per i comandi e
> [SETUP.md](SETUP.md) per l'installazione di JDK/Maven/Docker, il Maven
> Wrapper e la pipeline CI. Qui sotto restano solo i problemi **applicativi**
> (errori a runtime, non di ambiente) che si incontrano usando l'API.

### Troubleshooting / FAQ operative
- **L'app non parte: errore di validazione schema (Hibernate).** Le entita non
  combaciano con le tabelle Flyway. Verifica i tipi (soprattutto i timestamp). In
  emergenza si puo passare `ddl-auto` a `none`, ma la causa va corretta.
- **`mvn verify` fallisce subito.** Probabilmente Docker non e in esecuzione: i test
  `*IT` usano Testcontainers.
- **Connessione DB rifiutata.** Manca `docker compose up` o le porte sono occupate.
- **401 a ogni chiamata protetta.** Manca/scaduto l'access token: rifai il login o usa
  `/api/auth/refresh`.
- **403 su PUT.** Non sei il proprietario del documento e non sei ADMIN.
- **429 Too Many Requests.** Hai superato il rate limit per IP; attendi o alza
  `RATE_LIMIT_CAPACITY`.
- **L'evento non arriva su Kafka subito.** Normale: l'outbox lo invia col polling
  (default 5s). Controlla nella Kafka UI.
- **502 su `/api/profilo/...`.** Atteso: il MS esterno e ipotetico e non esiste.
- **Versione di OpenPDF/Flying Saucer non risolta.** Aggiorna la property nel pom.
- **Il PDF non si scarica (404).** Il documento non ha un PDF associato
  (`pdfRiferimento` nullo) o la chiave non esiste sullo storage.

### Dove guardare i log
Console dell'app. Ogni riga ha `reqId=...`: lo stesso valore lega tutte le righe di
una richiesta. Lo trovi anche nell'header di risposta `X-Request-Id`.

---

# Appendice J - Domande di colloquio avanzate

Domande piu "tricky", con risposta ragionata.

- *Le annotazioni `@Transactional`/`@Cacheable` funzionano se chiamo il metodo dallo
  stesso oggetto?* No: l'AOP di Spring agisce tramite proxy, le **chiamate interne**
  lo bypassano. Nel progetto `aggiorna` chiama `trova` internamente e la cache non si
  applica (voluto: dato fresco).
- *Perche l'outbox e non una transazione distribuita (2PC) tra DB e Kafka?* Le
  transazioni distribuite sono complesse e poco supportate; l'outbox ottiene
  affidabilita con strumenti semplici (una tabella + un poller), accettando
  at-least-once.
- *Differenza tra at-least-once ed exactly-once?* At-least-once: il messaggio puo
  duplicarsi (serve idempotenza). Exactly-once: nessun duplicato, ma piu costosa
  (transazioni Kafka, idempotent producer). Qui at-least-once + consumer idempotente.
- *Come gestisci un "poison message" su Kafka?* Con `ErrorHandlingDeserializer`: un
  messaggio non deserializzabile viene scartato senza bloccare il consumo (in
  produzione si aggiunge un dead-letter topic).
- *Perche ricaricare l'utente dal DB nel filtro JWT invece di fidarsi dei claim?* Per
  intercettare utenti disabilitati o con ruoli cambiati dopo l'emissione del token.
  Trade-off: una query per richiesta (mitigabile con cache).
- *Il rate limiter regge in cluster?* No: lo stato e in memoria locale. Servirebbe
  uno store condiviso (Redis) per un limite globale.
- *Perche `ddl-auto: validate` e non `update`?* `update` lascia che Hibernate
  modifichi lo schema: imprevedibile e pericoloso in produzione. `validate` +
  migrazioni versionate (Flyway) e l'approccio controllato.
- *Perche niente `delete` dei documenti?* Scelta di dominio: un protocollo non si
  "cancella"; al massimo si archivia (stato `ARCHIVIATO`).
- *Page direttamente nel JSON: problemi?* Il formato interno di `PageImpl` e instabile
  tra versioni; per questo si usa `@EnableSpringDataWebSupport(VIA_DTO)` che produce
  un JSON stabile.
- *Come testi codice che dipende da data/ora o id generati?* Si isola la dipendenza
  (qui l'id si simula con `thenAnswer` + reflection); per il tempo si userebbe un
  `Clock` iniettabile.
- *Constructor injection: perche e meglio?* Dipendenze esplicite e obbligatorie,
  campi `final` (immutabilita/thread-safety), test senza Spring, niente dipendenze
  nascoste.
- *Cosa succede se due richieste aggiornano lo stesso documento insieme?* Una vince,
  l'altra fallisce con `OptimisticLockException` (lock ottimistico via `@Version`).
- *Perche i DTO sono `record`?* Immutabili, concisi, con `equals/hashCode/toString`
  generati: ideali per oggetti di solo trasporto dati.

---

# Appendice K - Errori comuni e anti-pattern evitati

Cosa il progetto **evita** di proposito, e perche.

- **Esporre le entita JPA nelle API.** Si usano DTO: evita leak del modello, problemi
  di serializzazione lazy e accoppiamento API-DB.
- **Logica di business nei controller.** I controller sono sottili; la logica sta nel
  service. Piu testabile e riusabile.
- **Pubblicare su Kafka dentro la transazione, "a mano".** Sarebbe il dual-write
  problem; si usa l'outbox.
- **Password in chiaro o hash veloci.** Solo BCrypt con salt.
- **Sessioni server con JWT.** Sarebbe contraddittorio; l'app e stateless.
- **`ddl-auto: update` in produzione.** Schema non controllato; si usa Flyway.
- **Catturare le eccezioni in ogni controller.** Gestione centralizzata con
  `@RestControllerAdvice`.
- **Field injection (`@Autowired` sui campi).** Nasconde le dipendenze e complica i
  test; si usa il costruttore.
- **Doppia esecuzione del filtro JWT.** Evitata disabilitandone la registrazione
  servlet globale.
- **Fidarsi ciecamente del JSON di un servizio esterno.** Si mappa in modo difensivo
  su un DTO stabile.
- **Stato mutabile nei bean singleton.** I bean sono senza stato; l'unica eccezione
  (rate limiter) e resa thread-safe esplicitamente.

---

*Fine della guida. Per la visione d'insieme vedi [HLD.md](HLD.md) e [LLD.md](LLD.md);
per i pattern [PATTERNS.md](PATTERNS.md); per il codice, i sorgenti commentati in
`src/`. Buono studio e in bocca al lupo per il colloquio.*
