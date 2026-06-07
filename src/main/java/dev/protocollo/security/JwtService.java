package dev.protocollo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Servizio responsabile della creazione e validazione dei token JWT.
 *
 * Il token e firmato con algoritmo HMAC-SHA256 usando una chiave segreta
 * configurabile (proprieta {@code app.jwt.secret}). Le informazioni utili
 * (ruoli, nome) sono inserite come claim cosi da non dover interrogare il DB
 * solo per leggerle.
 */
@Service
public class JwtService {

    private final SecretKey chiaveFirma;
    private final long durataMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long durataMinuti) {
        // La chiave HMAC deve essere lunga almeno 256 bit (32 byte): vedi application.yml
        this.chiaveFirma = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.durataMillis = durataMinuti * 60_000L;
    }

    /**
     * Genera un token per l'utente autenticato, inserendo ruoli e nome come claim.
     *
     * I claim "ruoli" e "nome" sono puramente informativi: l'autorizzazione a
     * ogni richiesta passa comunque dal ricaricamento dell'utente dal DB
     * (vedi JwtAuthenticationFilter), per non fidarsi ciecamente del token.
     */
    public String generaToken(UtenteAutenticato utente) {
        Instant adesso = Instant.now();

        // Dalle authority "ROLE_USER"/"ROLE_ADMIN" ricavo i nomi puri dei ruoli
        List<String> ruoli = utente.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .toList();

        return Jwts.builder()
                .subject(utente.getUsername())
                .claim("ruoli", ruoli)
                .claim("nome", utente.getNomeCompleto())
                .issuedAt(Date.from(adesso))
                .expiration(Date.from(adesso.plusMillis(durataMillis)))
                .signWith(chiaveFirma)
                .compact();
    }

    /**
     * Estrae lo username (subject) dal token, verificandone firma e scadenza.
     * Se il token non e valido la libreria lancia un'eccezione runtime.
     */
    public String estraiUsername(String token) {
        return leggiClaim(token).getSubject();
    }

    /**
     * Verifica che il token sia ben formato, firmato correttamente e non scaduto.
     *
     * @return true se il token e valido, false altrimenti
     */
    public boolean isValido(String token) {
        try {
            leggiClaim(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Effettua il parsing del token verificandone la firma e restituisce i claim.
     */
    private Claims leggiClaim(String token) {
        return Jwts.parser()
                .verifyWith(chiaveFirma)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
