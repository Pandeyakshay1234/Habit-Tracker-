package com.habittracker.habit_tracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenApiConfig — OpenAPI 3 / Swagger Configuration with Global JWT Bearer Authentication
 *
 * Provides:
 *   1. Interactive documentation UI accessible at /swagger-ui.html
 *   2. OpenAPI JSON specification at /v3/api-docs
 *   3. Global "Authorize" button in Swagger UI to attach JWT tokens to all protected API calls
 */
@Configuration                                  // Marks this class as a Spring Bean configuration component
public class OpenApiConfig {

    // Unique identifier name for the JWT security scheme in Swagger UI
    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    /**
     * Configures the OpenAPI bean with custom application metadata and JWT Bearer security scheme.
     *
     * @return custom configured OpenAPI specification instance
     */
    @Bean                                       // Registers OpenAPI specification in Spring ApplicationContext
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                // ── 1. CONFIGURE API METADATA ─────────────────────────────
                .info(new Info()
                        .title("Habit Tracker REST API")
                        .description("Production-ready backend API for Habit Tracking featuring dynamic streak calculations, gamified streak freeze tokens, and automated email reminders.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Akshay Pandey")
                                .email("akshaypandey.apel@gmail.com")
                        )
                )
                // ── 2. APPLY SECURITY REQUIREMENT GLOBALLY ───────────────
                // Tells Swagger UI that all protected endpoints require the "Bearer Authentication" scheme
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                // ── 3. DEFINE JWT BEARER SECURITY SCHEME ─────────────────
                // Configures the interactive "Authorize" modal in Swagger UI
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)      // Standard HTTP Authentication
                                        .scheme("bearer")                    // Uses Bearer scheme
                                        .bearerFormat("JWT")                 // Signals token format is JWT
                                        .description("Enter your JWT token (without 'Bearer ' prefix). You can get a token from /api/v1/auth/register or /api/v1/auth/login.")
                        )
                );
    }
}
