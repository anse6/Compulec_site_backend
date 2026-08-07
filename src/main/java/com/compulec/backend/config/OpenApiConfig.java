package com.compulec.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Swagger / OpenAPI 3.
 * Accessible via : http://localhost:8080/api/swagger-ui.html
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                contact = @Contact(
                        name = "Compulec Team",
                        email = "contact@compulec.com",
                        url = "https://compulec.com"
                ),
                description = "API REST de l'application Compulec - Gestion complète avec authentification JWT",
                title = "Compulec Backend API",
                version = "1.0",
                license = @License(
                        name = "Licence MIT",
                        url = "https://choosealicense.com/licenses/mit"
                ),
                termsOfService = "Conditions d'utilisation"
        ),
        servers = {
                @Server(description = "Serveur Local (Développement)", url = "http://localhost:8080/api"),
                @Server(description = "Serveur de Production", url = "https://api.compulec.com")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT Bearer Token - Format : Bearer <token>",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
