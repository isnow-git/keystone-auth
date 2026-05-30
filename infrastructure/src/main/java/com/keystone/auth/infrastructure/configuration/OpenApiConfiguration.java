package com.keystone.auth.infrastructure.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Code-first OpenAPI metadata. The contract is the controllers + DTOs themselves; this class
 * supplies the document-level info (title, version, security schemes, server list) that springdoc
 * stitches around the per-endpoint annotations.
 */
@Configuration
public class OpenApiConfiguration {

  @Bean
  public OpenAPI keystoneAuthOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("keystone-auth")
                .version("0.1.0")
                .description(
                    """
                    Standalone authentication microservice. Issues short-lived RS256 JWT access
                    tokens and rotation-protected refresh tokens; publishes its verification key
                    at /auth/.well-known/jwks.json so any number of resource servers can verify
                    tokens locally without calling back.

                    Refresh tokens ride in an HttpOnly + Secure + SameSite=Strict cookie scoped
                    to /auth/refresh. Reusing a rotated token revokes the entire token family
                    (see ADR-0005).
                    """)
                .contact(
                    new Contact()
                        .name("keystone-auth")
                        .url("https://github.com/isnow-git/keystone-auth"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
        .servers(List.of(new Server().url("http://localhost:8080").description("local")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerJwt",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("RS256 access token issued by /auth/login"))
                .addSecuritySchemes(
                    "refreshCookie",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("refresh_token")
                        .description(
                            "HttpOnly refresh cookie issued by /auth/login, rotated on every"
                                + " /auth/refresh call.")));
  }
}
