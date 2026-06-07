package dev.protocollo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione della documentazione OpenAPI/Swagger.
 *
 * Dichiara lo schema di sicurezza "bearer JWT" cosi che nella UI di Swagger
 * compaia il pulsante "Authorize" per inserire il token e provare le API
 * protette direttamente dal browser.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEMA_SICUREZZA = "bearer-jwt";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Protocollo API")
                        .version("v1")
                        .description("API di esempio per la protocollazione di documenti."))
                // Applica di default lo schema di sicurezza a tutte le operazioni
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_SICUREZZA))
                .components(new Components().addSecuritySchemes(SCHEMA_SICUREZZA,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
