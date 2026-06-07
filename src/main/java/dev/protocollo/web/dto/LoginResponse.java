package dev.protocollo.web.dto;

/**
 * Risposta al login: contiene il token JWT da usare nelle chiamate successive
 * tramite l'header {@code Authorization: Bearer <token>}.
 */
public record LoginResponse(
        String token,
        String tipo,
        String nome) {

    public static LoginResponse bearer(String token, String nome) {
        return new LoginResponse(token, "Bearer", nome);
    }
}
