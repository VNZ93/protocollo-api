package dev.protocollo.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo delle richieste di refresh e di logout: contiene il refresh token.
 */
public record RefreshRequest(
        @NotBlank(message = "Il refresh token e obbligatorio")
        String refreshToken) {
}
