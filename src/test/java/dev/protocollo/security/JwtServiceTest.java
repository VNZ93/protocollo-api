package dev.protocollo.security;

import dev.protocollo.domain.Ruolo;
import dev.protocollo.domain.Utente;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitari del {@link JwtService}: generazione e validazione dei token.
 * Non serve il contesto di Spring, la classe viene istanziata direttamente.
 */
class JwtServiceTest {

    // Chiave di almeno 32 caratteri (256 bit), richiesta da HMAC-SHA256
    private final JwtService jwtService =
            new JwtService("chiave-di-test-lunga-abbastanza-per-hmac-sha256-0123456789", 60);

    private UtenteAutenticato utenteDiTest() {
        Utente utente = new Utente("mrossi", "hash", "Mario Rossi", Set.of(Ruolo.USER));
        return new UtenteAutenticato(utente);
    }

    @Test
    void generaUnTokenValidoDaCuiSiPuoEstrarreLoUsername() {
        String token = jwtService.generaToken(utenteDiTest());

        assertThat(token).isNotBlank();
        assertThat(jwtService.isValido(token)).isTrue();
        assertThat(jwtService.estraiUsername(token)).isEqualTo("mrossi");
    }

    @Test
    void riconosceUnTokenManomessoComeNonValido() {
        String token = jwtService.generaToken(utenteDiTest());
        String tokenManomesso = token + "abc";

        assertThat(jwtService.isValido(tokenManomesso)).isFalse();
    }

    @Test
    void unTokenFirmatoConUnaChiaveDiversaNonEValido() {
        JwtService altroService =
                new JwtService("una-chiave-completamente-diversa-ma-lunga-abbastanza-xyz", 60);
        String tokenAltrui = altroService.generaToken(utenteDiTest());

        assertThat(jwtService.isValido(tokenAltrui)).isFalse();
    }
}
