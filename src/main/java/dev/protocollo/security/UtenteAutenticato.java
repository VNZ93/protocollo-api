package dev.protocollo.security;

import dev.protocollo.domain.Ruolo;
import dev.protocollo.domain.Utente;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adattatore tra la nostra entita {@link Utente} e il contratto
 * {@link UserDetails} di Spring Security.
 *
 * E questo l'oggetto che finisce nel SecurityContext e che i controller
 * possono ricevere con {@code @AuthenticationPrincipal UtenteAutenticato}.
 */
public class UtenteAutenticato implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String nomeCompleto;
    private final boolean attivo;
    private final List<GrantedAuthority> authorities;

    public UtenteAutenticato(Utente utente) {
        this.id = utente.getId();
        this.username = utente.getUsername();
        this.password = utente.getPassword();
        this.nomeCompleto = utente.getNomeCompleto();
        this.attivo = utente.isAttivo();
        this.authorities = utente.getRuoli().stream()
                // Convenzione di Spring Security: le authority dei ruoli iniziano con "ROLE_"
                .map(ruolo -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + ruolo.name()))
                .toList();
    }

    public Long getId() {
        return id;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    /** Comodo per i controlli sui permessi: l'utente ha il ruolo ADMIN? */
    public boolean isAmministratore() {
        return authorities.contains(new SimpleGrantedAuthority("ROLE_" + Ruolo.ADMIN.name()));
    }

    // --- Metodi del contratto UserDetails ------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return attivo;
    }
}
