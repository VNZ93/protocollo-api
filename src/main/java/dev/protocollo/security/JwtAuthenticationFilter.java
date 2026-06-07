package dev.protocollo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro che intercetta ogni richiesta HTTP, legge l'header
 * {@code Authorization: Bearer <token>}, valida il JWT e, se valido,
 * popola il SecurityContext con l'utente autenticato.
 *
 * Estende {@link OncePerRequestFilter} per garantire una sola esecuzione
 * per richiesta. Viene inserito nella filter chain prima del filtro standard
 * di autenticazione username/password (vedi SecurityConfig).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFISSO = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = estraiToken(request);

        // Se non c'e token, oppure il contesto e gia popolato, passo oltre.
        // Le rotte pubbliche (login, swagger) verranno comunque permesse da SecurityConfig.
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtService.isValido(token)) {
            log.debug("Token JWT non valido o scaduto per la richiesta {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Token valido: ricarico l'utente dal DB (cosi intercetto utenti disabilitati)
        String username = jwtService.estraiUsername(token);
        UserDetails utente = userDetailsService.loadUserByUsername(username);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(utente, null, utente.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Utente '{}' autenticato tramite JWT", username);

        filterChain.doFilter(request, response);
    }

    /**
     * Estrae il token dall'header Authorization, rimuovendo il prefisso "Bearer ".
     *
     * @return il token, oppure null se l'header e assente o malformato
     */
    private String estraiToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFISSO)) {
            return header.substring(PREFISSO.length());
        }
        return null;
    }
}
