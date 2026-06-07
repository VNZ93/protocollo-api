package dev.protocollo.config;

import dev.protocollo.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configurazione centrale di Spring Security.
 *
 * Definisce una filter chain stateless (nessuna sessione lato server: lo stato
 * dell'autenticazione vive nel token JWT) e registra il nostro
 * {@link JwtAuthenticationFilter} prima del filtro standard username/password.
 *
 * {@code @EnableMethodSecurity} abilita le annotazioni come {@code @PreAuthorize}
 * sui metodi dei controller/service.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Rotte accessibili senza autenticazione. */
    private static final String[] ROTTE_PUBBLICHE = {
            "/api/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF non serve: l'API e stateless e non usa cookie di sessione
                .csrf(AbstractHttpConfigurer::disable)
                // Nessuna sessione HTTP: ogni richiesta porta con se il proprio token
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Regole di autorizzazione sulle rotte
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTTE_PUBBLICHE).permitAll()
                        .anyRequest().authenticated())
                // Risposta 401 (invece di redirect) quando manca l'autenticazione
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint()))
                // Inserisco il filtro JWT prima del filtro username/password standard
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Disabilita la registrazione automatica del filtro JWT come filtro servlet
     * globale: deve essere eseguito SOLO dentro la filter chain di Spring
     * Security (dove lo inseriamo con addFilterBefore), non due volte.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> registrazioneFiltroJwt(
            JwtAuthenticationFilter filtro) {
        FilterRegistrationBean<JwtAuthenticationFilter> registrazione =
                new FilterRegistrationBean<>(filtro);
        registrazione.setEnabled(false);
        return registrazione;
    }

    /**
     * Encoder usato per cifrare e verificare le password (BCrypt, con salt).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Espone l'AuthenticationManager: lo usa l'AuthController per autenticare
     * le credenziali in fase di login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Entry point che restituisce semplicemente lo stato 401 quando una
     * richiesta protetta arriva senza un token valido.
     */
    private AuthenticationEntryPoint entryPoint() {
        return (request, response, authException) ->
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Autenticazione richiesta");
    }
}
