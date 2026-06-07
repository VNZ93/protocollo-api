package dev.protocollo.service;

import dev.protocollo.domain.RefreshToken;
import dev.protocollo.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Gestisce il ciclo di vita dei refresh token: creazione, validazione,
 * rotazione e revoca.
 *
 * I token sono opachi (UUID casuali) e persistiti: questo li rende revocabili,
 * a differenza dell'access token JWT che e stateless.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration durata;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-days}") long durataGiorni) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.durata = Duration.ofDays(durataGiorni);
    }

    /**
     * Crea e salva un nuovo refresh token per l'utente indicato.
     */
    @Transactional
    public RefreshToken crea(String username) {
        RefreshToken token = new RefreshToken(
                UUID.randomUUID().toString(),
                username,
                Instant.now().plus(durata));
        return refreshTokenRepository.save(token);
    }

    /**
     * Verifica che il token esista e sia ancora utilizzabile.
     *
     * @throws RefreshTokenNonValidoException se mancante, scaduto o revocato
     */
    @Transactional(readOnly = true)
    public RefreshToken verifica(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RefreshTokenNonValidoException("Refresh token non valido"));
        if (!refreshToken.isUtilizzabile()) {
            throw new RefreshTokenNonValidoException("Refresh token scaduto o revocato");
        }
        return refreshToken;
    }

    /**
     * Ruota il token: revoca quello attuale e ne emette uno nuovo per lo stesso
     * utente. La rotazione a ogni uso limita la finestra di rischio in caso di
     * furto del token.
     */
    @Transactional
    public RefreshToken ruota(RefreshToken attuale) {
        attuale.setRevocato(true);
        refreshTokenRepository.save(attuale);
        return crea(attuale.getUsername());
    }

    /**
     * Revoca un token (usato in fase di logout). Non fallisce se il token non
     * esiste: il logout deve essere idempotente.
     */
    @Transactional
    public void revoca(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevocato(true);
            refreshTokenRepository.save(rt);
        });
    }
}
