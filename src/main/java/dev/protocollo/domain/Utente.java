package dev.protocollo.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/**
 * Entita JPA che rappresenta un utente applicativo.
 *
 * La password e memorizzata gia cifrata con BCrypt (mai in chiaro).
 * I ruoli sono salvati in una tabella separata {@code utente_ruolo}.
 */
@Entity
@Table(name = "utente")
public class Utente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** Hash BCrypt della password. */
    @Column(nullable = false)
    private String password;

    @Column(name = "nome_completo", nullable = false, length = 150)
    private String nomeCompleto;

    /** Flag per disabilitare un utente senza cancellarlo. */
    @Column(nullable = false)
    private boolean attivo = true;

    /**
     * Ruoli dell'utente, caricati subito (EAGER) perche servono a ogni
     * autenticazione per costruire le authority.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "utente_ruolo", joinColumns = @JoinColumn(name = "utente_id"))
    @Column(name = "ruolo", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Ruolo> ruoli = new HashSet<>();

    protected Utente() {
    }

    public Utente(String username, String password, String nomeCompleto, Set<Ruolo> ruoli) {
        this.username = username;
        this.password = password;
        this.nomeCompleto = nomeCompleto;
        this.ruoli = ruoli;
        this.attivo = true;
    }

    // --- Getter e setter -----------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public boolean isAttivo() {
        return attivo;
    }

    public void setAttivo(boolean attivo) {
        this.attivo = attivo;
    }

    public Set<Ruolo> getRuoli() {
        return ruoli;
    }
}
