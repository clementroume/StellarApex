package apex.stellar.antares.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger configuration for Antares Auth API. */
@Configuration
public class OpenApiConfig {

  @Value("${application.frontend.url}")
  private String serverUrl;

  /**
   * Configures and returns the OpenAPI specification for the Antares Auth API. The API provides
   * Authentication, Authorization, and User Management services. It includes details such as the
   * API title, description, version, contact information, license information, server URL, and
   * security schemes.
   *
   * @return an OpenAPI object containing the configuration for the Antares Auth API.
   */
  @Bean
  public OpenAPI antaresOpenApi() {

    return new OpenAPI()
        .info(
            new Info()
                .title("Antares Auth Api")
                .description("Authentication, Authorization & User Management Service")
                .version("v1.0.0")
                .contact(
                    new Contact()
                        .name("StellarApex GitHub")
                        .url("https://github.com/clementroume/StellarApex"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
        .servers(List.of(new Server().url(serverUrl).description("Main Server (Traefik)")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the 'accessToken'")));
  }
}
