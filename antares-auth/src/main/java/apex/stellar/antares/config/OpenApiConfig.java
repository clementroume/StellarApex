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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger configuration for Antares Auth API. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI antaresOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Antares Auth API")
                .description("Authentication, Authorization & User Management Service")
                .version("v1.0.0")
                .contact(
                    new Contact()
                        .name("StellarApex Team")
                        .email("dev@stellarapex.com")
                        .url("https://stellar.apex"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
        .servers(
            List.of(
                new Server().url("https://stellar.apex").description("Production"),
                new Server().url("http://localhost:8080").description("Local Development")))
        .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "cookieAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("stellar_access_token")
                        .description("JWT Access Token (HttpOnly Cookie)")));
  }
}
