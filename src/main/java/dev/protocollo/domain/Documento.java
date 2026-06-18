package dev.protocollo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Entita JPA che rappresenta un documento da protocollare.
 *
 * La tabella e creata dalle migrazioni Flyway (vedi db/migration), non da
 * Hibernate: in produzione lo schema deve essere versionato, non generato
 * automaticamente. Hibernate qui si limita a mappare classe e tabella.
 */
@Entity
@Table(name = "documento")
public class Documento {

    /** Chiave primaria, generata dalla sequenza/identity del database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titolo;

    /** Corpo del documento, tipicamente lungo: mappato come TEXT. */
    @Column(columnDefinition = "text")
    private String contenuto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoDocumento stato;

    /** Numero di protocollo: valorizzato solo quando il documento e protocollato. */
    @Column(name = "numero_protocollo", length = 50)
    private String numeroProtocollo;

    /**
     * Username del proprietario del documento.
     * Serve a verificare i permessi: un utente non amministratore puo
     * modificare soltanto i documenti che ha creato.
     */
    @Column(nullable = false, length = 100)
    private String proprietario;

    /** Riferimento (chiave) del PDF generato e salvato sull'object storage. */
    @Column(name = "pdf_riferimento", length = 500)
    private String pdfRiferimento;

    /** Indica se la risorsa risulta allineata con l'indice esterno. */
    @Column(nullable = false)
    private boolean indicizzato = false;

    /** Momento dell'ultimo allineamento ricevuto dall'indice esterno. */
    @Column(name = "data_indicizzazione")
    private Instant dataIndicizzazione;

    /**
     * Tag indipendente dallo stato: un documento archiviato resta nel suo
     * stato del ciclo di vita (bozza/approvata/protocollato) ma non e piu
     * operativo e finisce nel tab "Archivio".
     */
    @Column(nullable = false)
    private boolean archiviato = false;

    /** Momento dell'approvazione da parte di un amministratore. */
    @Column(name = "data_approvazione")
    private Instant dataApprovazione;

    /**
     * Indica che il documento e gia stato accodato per la protocollazione
     * automatica (messaggio scritto sull'outbox/Kafka): evita che il job di
     * scansione lo rimetta in coda piu volte mentre il consumer lo elabora.
     */
    @Column(name = "protocollazione_in_coda", nullable = false)
    private boolean protocollazioneInCoda = false;

    @Column(name = "data_creazione", nullable = false, updatable = false)
    private Instant dataCreazione;

    @Column(name = "data_aggiornamento", nullable = false)
    private Instant dataAggiornamento;

    /**
     * Colonna di versione per il lock ottimistico: Hibernate la incrementa a
     * ogni update e lancia un'eccezione se due transazioni concorrenti
     * tentano di modificare la stessa riga.
     */
    @Version
    private Long version;

    /** Costruttore vuoto richiesto da JPA/Hibernate. */
    protected Documento() {
    }

    public Documento(String titolo, String contenuto, String proprietario) {
        this.titolo = titolo;
        this.contenuto = contenuto;
        this.proprietario = proprietario;
        this.stato = StatoDocumento.BOZZA;
    }

    /**
     * Callback eseguita prima del primo INSERT: imposta le date di audit.
     */
    @PrePersist
    void prePersist() {
        Instant adesso = Instant.now();
        this.dataCreazione = adesso;
        this.dataAggiornamento = adesso;
        if (this.stato == null) {
            this.stato = StatoDocumento.BOZZA;
        }
    }

    /**
     * Callback eseguita prima di ogni UPDATE: aggiorna la data di modifica.
     */
    @PreUpdate
    void preUpdate() {
        this.dataAggiornamento = Instant.now();
    }

    // --- Getter e setter -----------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getTitolo() {
        return titolo;
    }

    public void setTitolo(String titolo) {
        this.titolo = titolo;
    }

    public String getContenuto() {
        return contenuto;
    }

    public void setContenuto(String contenuto) {
        this.contenuto = contenuto;
    }

    public StatoDocumento getStato() {
        return stato;
    }

    public void setStato(StatoDocumento stato) {
        this.stato = stato;
    }

    public String getNumeroProtocollo() {
        return numeroProtocollo;
    }

    public void setNumeroProtocollo(String numeroProtocollo) {
        this.numeroProtocollo = numeroProtocollo;
    }

    public String getProprietario() {
        return proprietario;
    }

    public void setProprietario(String proprietario) {
        this.proprietario = proprietario;
    }

    public String getPdfRiferimento() {
        return pdfRiferimento;
    }

    public void setPdfRiferimento(String pdfRiferimento) {
        this.pdfRiferimento = pdfRiferimento;
    }

    public boolean isIndicizzato() {
        return indicizzato;
    }

    public void setIndicizzato(boolean indicizzato) {
        this.indicizzato = indicizzato;
    }

    public Instant getDataIndicizzazione() {
        return dataIndicizzazione;
    }

    public void setDataIndicizzazione(Instant dataIndicizzazione) {
        this.dataIndicizzazione = dataIndicizzazione;
    }

    public Instant getDataCreazione() {
        return dataCreazione;
    }

    public Instant getDataAggiornamento() {
        return dataAggiornamento;
    }

    public boolean isArchiviato() {
        return archiviato;
    }

    public void setArchiviato(boolean archiviato) {
        this.archiviato = archiviato;
    }

    public Instant getDataApprovazione() {
        return dataApprovazione;
    }

    public void setDataApprovazione(Instant dataApprovazione) {
        this.dataApprovazione = dataApprovazione;
    }

    public boolean isProtocollazioneInCoda() {
        return protocollazioneInCoda;
    }

    public void setProtocollazioneInCoda(boolean protocollazioneInCoda) {
        this.protocollazioneInCoda = protocollazioneInCoda;
    }

    public Long getVersion() {
        return version;
    }
}
