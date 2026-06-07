package dev.protocollo.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import dev.protocollo.security.JwtService;
import dev.protocollo.security.UtenteAutenticato;
import dev.protocollo.web.dto.LoginRequest;
import dev.protocollo.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller di autenticazione: espone l'endpoint di login che, a fronte di
 * credenziali valide, restituisce un token JWT.
 *
 * Questa rotta e pubblica (vedi SecurityConfig): e l'unico modo per ottenere
 * un token prima di poter chiamare le API protette.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticazione", description = "Login e rilascio del token JWT")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica un utente e restituisce un token JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest richiesta) {
        // Delego a Spring Security la verifica delle credenziali: se sono
        // errate viene lanciata una BadCredentialsException (gestita -> 401)
        Authentication autenticazione = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(richiesta.username(), richiesta.password()));

        UtenteAutenticato utente = (UtenteAutenticato) autenticazione.getPrincipal();
        String token = jwtService.generaToken(utente);

        log.info("Login riuscito per l'utente '{}'", utente.getUsername());
        return LoginResponse.bearer(token, utente.getNomeCompleto());
    }
}
