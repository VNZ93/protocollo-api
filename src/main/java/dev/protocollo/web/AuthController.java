package dev.protocollo.web;

import dev.protocollo.domain.RefreshToken;
import dev.protocollo.security.CustomUserDetailsService;
import dev.protocollo.security.JwtService;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.service.RefreshTokenService;
import dev.protocollo.web.dto.LoginRequest;
import dev.protocollo.web.dto.RefreshRequest;
import dev.protocollo.web.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller di autenticazione.
 *
 * Espone:
 *  - POST /api/auth/login    verifica le credenziali e rilascia access + refresh token
 *  - POST /api/auth/refresh  scambia un refresh token valido con una nuova coppia
 *  - POST /api/auth/logout   revoca il refresh token
 *
 * Queste rotte sono pubbliche (vedi SecurityConfig): sono il modo per ottenere
 * un token prima di poter chiamare le API protette.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticazione", description = "Login, refresh e logout")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CustomUserDetailsService userDetailsService;

    public AuthController(AuthenticationManager authenticationManager,
                         JwtService jwtService,
                         RefreshTokenService refreshTokenService,
                         CustomUserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica un utente e restituisce access token e refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest richiesta) {
        // Delego a Spring Security la verifica delle credenziali: se sono
        // errate viene lanciata una BadCredentialsException (gestita -> 401)
        Authentication autenticazione = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(richiesta.username(), richiesta.password()));

        UtenteAutenticato utente = (UtenteAutenticato) autenticazione.getPrincipal();

        String accessToken = jwtService.generaToken(utente);
        String refreshToken = refreshTokenService.crea(utente.getUsername()).getToken();

        log.info("Login riuscito per l'utente '{}'", utente.getUsername());
        return TokenResponse.bearer(accessToken, refreshToken, utente.getNomeCompleto());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rilascia un nuovo access token a partire da un refresh token valido")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest richiesta) {
        // Verifico il refresh token e lo ruoto (il vecchio viene revocato)
        RefreshToken corrente = refreshTokenService.verifica(richiesta.refreshToken());
        UtenteAutenticato utente =
                (UtenteAutenticato) userDetailsService.loadUserByUsername(corrente.getUsername());

        RefreshToken nuovo = refreshTokenService.ruota(corrente);
        String accessToken = jwtService.generaToken(utente);

        log.info("Refresh token usato per l'utente '{}'", utente.getUsername());
        return TokenResponse.bearer(accessToken, nuovo.getToken(), utente.getNomeCompleto());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoca il refresh token (logout)")
    public void logout(@Valid @RequestBody RefreshRequest richiesta) {
        refreshTokenService.revoca(richiesta.refreshToken());
    }
}
