package dev.protocollo.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo della richiesta di login.
 * Le annotazioni di Bean Validation garantiscono che i campi non siano vuoti.
 */
public record LoginRequest(
        @NotBlank(message = "Lo username e obbligatorio")
        String username,

        @NotBlank(message = "La password e obbligatoria")
        String password) {
}
