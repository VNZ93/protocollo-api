package dev.protocollo.config;

import dev.protocollo.domain.Ruolo;
import dev.protocollo.domain.Utente;
import dev.protocollo.repository.UtenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Popola il database con due utenti di esempio al primo avvio.
 *
 * Si e scelto un seeder applicativo (invece di una migrazione Flyway) perche
 * cosi le password vengono cifrate con lo stesso {@link PasswordEncoder}
 * dell'applicazione, senza dover incollare hash precalcolati nello script SQL.
 * L'operazione e idempotente: se gli utenti esistono gia non fa nulla.
 *
 * NOTA: e materiale dimostrativo. In un sistema reale gli utenti non vanno
 * creati con password in chiaro nel codice sorgente.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UtenteRepository utenteRepository, PasswordEncoder passwordEncoder) {
        this.utenteRepository = utenteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        creaSeNonEsiste("admin", "admin123", "Amministratore di sistema",
                "admin@example.com", Set.of(Ruolo.ADMIN, Ruolo.USER));
        creaSeNonEsiste("mrossi", "password123", "Mario Rossi",
                "mario.rossi@example.com", Set.of(Ruolo.USER));
    }

    private void creaSeNonEsiste(String username, String passwordInChiaro,
                                 String nomeCompleto, String email, Set<Ruolo> ruoli) {
        if (utenteRepository.findByUsername(username).isPresent()) {
            return;
        }
        Utente utente = new Utente(
                username,
                passwordEncoder.encode(passwordInChiaro),
                nomeCompleto,
                ruoli);
        utente.setEmail(email);
        utenteRepository.save(utente);
        log.info("Creato utente di esempio '{}' con ruoli {}", username, ruoli);
    }
}
