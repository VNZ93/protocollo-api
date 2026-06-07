package dev.protocollo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Riga della tabella outbox: rappresenta un evento di dominio da pubblicare su
 * Kafka. Viene salvata nella stessa transazione del cambiamento di business, e
 * pubblicata in un secondo momento dal publisher schedulato (pattern Outbox).
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tipo logico dell'evento (es. "PROTOCOLLAZIONE"): serve a deserializzarlo. */
    @Column(nullable = false, length = 50)
    private String tipo;

    /** Id dell'aggregato a cui si riferisce l'evento (es. id del documento). */
    @Column(name = "aggregate_id", length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 200)
    private String topic;

    /** Chiave del messaggio Kafka (per il partizionamento). */
    @Column(length = 200)
    private String chiave;

    /** Corpo dell'evento serializzato in JSON. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "creato_il", nullable = false, updatable = false)
    private Instant creatoIl;

    @Column(nullable = false)
    private boolean pubblicato = false;

    @Column(name = "pubblicato_il")
    private Instant pubblicatoIl;

    protected OutboxEvent() {
    }

    public OutboxEvent(String tipo, String aggregateId, String topic, String chiave, String payload) {
        this.tipo = tipo;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.chiave = chiave;
        this.payload = payload;
    }

    @PrePersist
    void prePersist() {
        this.creatoIl = Instant.now();
    }

    /** Segna l'evento come pubblicato, registrandone il momento. */
    public void segnaPubblicato() {
        this.pubblicato = true;
        this.pubblicatoIl = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getChiave() {
        return chiave;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPubblicato() {
        return pubblicato;
    }
}
