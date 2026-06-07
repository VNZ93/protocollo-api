# Pattern e best practice usati nel progetto

Questo documento elenca i principali pattern di programmazione e le buone
pratiche presenti nel codice, con un riferimento ai file dove vederli "dal vivo".
L'obiettivo non e usare i pattern per il gusto di farlo, ma riconoscere quelli
che emergono naturalmente da un'applicazione Spring Boot ben strutturata.

---

## Pattern strutturali e creazionali

### Dependency Injection / Inversion of Control
Le classi non costruiscono le proprie dipendenze: le ricevono dal costruttore, e
Spring le inietta. Questo rende il codice testabile (nei test passo dei mock) e
disaccoppiato.
- Esempio: [DocumentoService](../src/main/java/dev/protocollo/service/DocumentoService.java) riceve repository, storage, ecc. nel costruttore.
- Best practice: **constructor injection** (campi `final`), non `@Autowired` sui campi.

### Singleton
I bean Spring sono singleton per default: ne esiste una sola istanza nel contesto,
condivisa e thread-safe perche senza stato mutabile. Non si implementa "a mano"
il singleton (con costruttori privati), ci pensa il container.
- Esempio: tutti i `@Service`, `@Component`, `@Configuration`.

### Strategy
Un comportamento ha piu implementazioni intercambiabili dietro un'unica
interfaccia; quale usare si decide a runtime (qui in base al profilo attivo).
- Interfaccia: [DocumentStorage](../src/main/java/dev/protocollo/storage/DocumentStorage.java)
- Strategie: [LocalFileSystemStorage](../src/main/java/dev/protocollo/storage/LocalFileSystemStorage.java) (profilo `dev`) e [S3ObjectStorage](../src/main/java/dev/protocollo/storage/S3ObjectStorage.java) (profilo `prod`).
- Il `DocumentoService` dipende dall'interfaccia: non sa quale strategia e attiva.

### Adapter
Si "adatta" un oggetto a un'interfaccia attesa da un altro componente.
- Esempio: [UtenteAutenticato](../src/main/java/dev/protocollo/security/UtenteAutenticato.java) adatta la nostra entita `Utente` all'interfaccia `UserDetails` richiesta da Spring Security.

### Factory Method (statico)
Metodi statici che costruiscono oggetti con un nome che ne esprime l'intento,
piu leggibili di un costruttore con tanti parametri.
- Esempi: `ProtocollazioneEvent.di(...)`, `TokenResponse.bearer(...)`, `DocumentoResponse.da(...)`.

### Builder
Costruzione passo-passo di oggetti complessi con API fluente.
- Esempi (da librerie): `Jwts.builder()...` in [JwtService](../src/main/java/dev/protocollo/security/JwtService.java), `TopicBuilder` in [KafkaTopicConfig](../src/main/java/dev/protocollo/config/KafkaTopicConfig.java), i `...Request.builder()` dell'SDK S3.

---

## Pattern comportamentali

### Template Method
Una classe base definisce lo scheletro di un algoritmo e lascia ai sottotipi il
"riempimento" di un passo.
- Esempio: i filtri estendono `OncePerRequestFilter` e implementano solo
  `doFilterInternal`: [JwtAuthenticationFilter](../src/main/java/dev/protocollo/security/JwtAuthenticationFilter.java), [RequestLoggingFilter](../src/main/java/dev/protocollo/common/logging/RequestLoggingFilter.java), [RateLimitingFilter](../src/main/java/dev/protocollo/common/ratelimit/RateLimitingFilter.java).

### Chain of Responsibility
Una richiesta attraversa una catena di gestori, ognuno dei quali decide se
elaborarla e/o passarla al successivo.
- Esempio: la filter chain HTTP (logging -> rate limit -> sicurezza/JWT -> controller).

### Producer / Consumer
Disaccoppiamento tramite messaggi: chi produce non conosce chi consuma.
- Producer: gli eventi di protocollazione (via outbox) verso Kafka.
- Consumer: [IndiceConsumer](../src/main/java/dev/protocollo/messaging/IndiceConsumer.java) per gli aggiornamenti dall'indice esterno.

### Specification
I criteri di query sono oggetti componibili, invece di una query fissa per ogni
combinazione di filtri.
- Esempio: [DocumentoSpecifications](../src/main/java/dev/protocollo/repository/DocumentoSpecifications.java) costruisce la query dai filtri presenti.

---

## Pattern architetturali

### Architettura a livelli (layered)
Responsabilita separate: `web` (controller/DTO) -> `service` (logica) ->
`repository` (persistenza). Ogni livello dipende solo da quello sotto.

### Repository
L'accesso ai dati e dietro un'interfaccia; Spring Data ne genera
l'implementazione. Il dominio non conosce SQL ne JDBC.
- Esempio: [DocumentoRepository](../src/main/java/dev/protocollo/repository/DocumentoRepository.java).

### DTO (Data Transfer Object)
Oggetti dedicati allo scambio dei dati con l'esterno, distinti dalle entita JPA.
Evitano di esporre il modello di persistenza e disaccoppiano API e database.
- Esempi: i record in [web/dto](../src/main/java/dev/protocollo/web/dto).

### Transactional Outbox
Per pubblicare eventi in modo affidabile, l'evento viene scritto su una tabella
`outbox` nella **stessa transazione** del cambiamento di business; un publisher
schedulato lo invia poi a Kafka. Risolve il "dual write problem" (dato salvato ma
evento perso, o viceversa).
- Scrittura: [OutboxService](../src/main/java/dev/protocollo/messaging/outbox/OutboxService.java)
- Invio: [OutboxPublisher](../src/main/java/dev/protocollo/messaging/outbox/OutboxPublisher.java)

### Anti-corruption layer (mappatura difensiva)
Quando si integra un servizio esterno, si mappa la sua risposta su un nostro DTO
stabile, isolando il resto dell'app dai cambiamenti del formato remoto.
- Esempio: [AnagraficaClient](../src/main/java/dev/protocollo/client/AnagraficaClient.java) legge il JSON con `path()` e nomi di campo alternativi.

---

## Altre buone pratiche

- **Immutabilita**: DTO ed eventi sono `record` immutabili.
- **Configurazione esternalizzata**: valori in `application.yml` e variabili
  d'ambiente; binding type-safe con `@ConfigurationProperties`
  ([AccreditamentoProperties](../src/main/java/dev/protocollo/config/AccreditamentoProperties.java)).
- **Gestione errori centralizzata**: `@RestControllerAdvice` con `ProblemDetail`
  ([GlobalExceptionHandler](../src/main/java/dev/protocollo/web/GlobalExceptionHandler.java)).
- **Lock ottimistico** (`@Version`) per la concorrenza sui documenti.
- **Idempotenza**: il consumer e il logout sono progettati per essere ripetibili
  senza effetti collaterali.
- **Fail-safe**: l'invio eventi non blocca le richieste; un messaggio Kafka
  malformato non blocca il consumer (`ErrorHandlingDeserializer`).
- **Sicurezza**: password con BCrypt, JWT firmato, access token di breve durata +
  refresh token revocabile, rate limiting.
