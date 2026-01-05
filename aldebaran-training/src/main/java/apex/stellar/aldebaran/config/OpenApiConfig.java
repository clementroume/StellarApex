package apex.stellar.aldebaran.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI/Swagger configuration for Aldebaran Training API. */
@Configuration
public class OpenApiConfig {

  @Value("${application.frontend.url}")
  private String serverUrl;

  /**
   * Configures and provides the OpenAPI specification for the Aldebaran Training API. The
   * specification includes metadata such as the API title, description, version, contact
   * information, license details, server configuration, and security requirements.
   *
   * @return an instance of {@link OpenAPI} that defines the API's metadata, server, and security
   *     configuration.
   */
  @Bean
  public OpenAPI aldebaranOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Aldebaran Training API")
                .description("Workout Management, Exercise Catalog & Performance Analytics")
                .version("v1.0.0")
                .contact(
                    new Contact()
                        .name("StellarApex GitHub")
                        .url("https://github.com/clementroume/StellarApex"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
        .servers(List.of(new Server().url(serverUrl).description("Main Server (Traefik)")));
  }
}
