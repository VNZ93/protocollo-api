package dev.protocollo.web.dto;

/**
 * Risposta contenente la coppia di token, restituita sia dal login sia dal
 * refresh.
 *
 * @param accessToken  JWT di breve durata da usare nelle chiamate protette
 * @param refreshToken token opaco di lunga durata per ottenere un nuovo access token
 * @param tipo         schema di autorizzazione da usare ("Bearer")
 * @param nome         nome completo dell'utente (comodita per la UI)
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tipo,
        String nome) {

    public static TokenResponse bearer(String accessToken, String refreshToken, String nome) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", nome);
    }
}
