package dev.protocollo.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo delle richieste di creazione (POST) e aggiornamento (PUT) di un documento.
 *
 * I vincoli di validazione vengono applicati automaticamente nei controller
 * grazie all'annotazione {@code @Valid}.
 */
public record DocumentoRequest(
        @NotBlank(message = "Il titolo e obbligatorio")
        @Size(max = 200, message = "Il titolo non puo superare i 200 caratteri")
        String titolo,

        @Size(max = 20_000, message = "Il contenuto e troppo lungo")
        String contenuto) {
}
