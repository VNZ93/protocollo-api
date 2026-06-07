package dev.protocollo.security;

import dev.protocollo.repository.UtenteRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carica i dati dell'utente dal database a partire dallo username.
 *
 * Spring Security usa questo servizio sia in fase di login (per verificare
 * la password) sia nel filtro JWT (per ricostruire il principal a ogni
 * richiesta autenticata, cosi da intercettare utenti disabilitati).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UtenteRepository utenteRepository;

    public CustomUserDetailsService(UtenteRepository utenteRepository) {
        this.utenteRepository = utenteRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return utenteRepository.findByUsername(username)
                .map(UtenteAutenticato::new)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utente non trovato: " + username));
    }
}
