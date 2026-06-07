package dev.protocollo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Refresh token persistito lato server.
 *
 * E un token opaco (una stringa casuale, non un JWT) cosi da poterlo revocare:
 * essendo salvato sul database, possiamo invalidarlo al logout o ruotarlo a
 * ogni utilizzo. L'access token invece resta uno JWT stateless e di breve durata.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private Instant scadenza;

    @Column(nullable = false)
    private boolean revocato = false;

    protected RefreshToken() {
    }

    public RefreshToken(String token, String username, Instant scadenza) {
        this.token = token;
        this.username = username;
        this.scadenza = scadenza;
        this.revocato = false;
    }

    /** Il token e utilizzabile se non revocato e non scaduto. */
    public boolean isUtilizzabile() {
        return !revocato && scadenza.isAfter(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public Instant getScadenza() {
        return scadenza;
    }

    public boolean isRevocato() {
        return revocato;
    }

    public void setRevocato(boolean revocato) {
        this.revocato = revocato;
    }
}
