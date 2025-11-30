package apex.stellar.bellatrix.config;

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

/** Aggregates multiple OpenAPI specifications from different microservices. */
@Configuration
public class BellatrixConfig {

  @Value("${swagger.services.antares.url}")
  private String antaresUrl;

  @Value("${swagger.services.aldebaran.url}")
  private String aldebaranUrl;

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("StellarApex Platform API")
                .description("Unified API documentation for all microservices")
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
        .servers(List.of(new Server().url("https://stellar.apex").description("Production")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth").addList("cookieAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT Token from Antares Auth"))
                .addSecuritySchemes(
                    "cookieAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("stellar_access_token")
                        .description("JWT Access Token (HttpOnly Cookie)")));
  }

  //  /** Group for Antares Auth API. */
  //  @Bean
  //  public GroupedOpenApi antaresAuthApi() {
  //    return GroupedOpenApi.builder()
  //        .group("1-antares-auth")
  //        .displayName("Antares Auth")
  //        .pathsToMatch("/antares/**")
  //        .build();
  //  }
  //
  //  /** Group for Orion Training API. */
  //  @Bean
  //  public GroupedOpenApi aldebaranTrainingApi() {
  //    return GroupedOpenApi.builder()
  //        .group("2-aldebaran-training")
  //        .displayName("Aldebaran Training")
  //        .pathsToMatch("/aldebaran/**")
  //        .build();
  //  }

  //  /** Example for future services - uncomment when ready. */
  //  @Bean
  //  public GroupedOpenApi vegaPlanningApi() {
  //    return GroupedOpenApi.builder()
  //        .group("3-vega-planning")
  //        .displayName("📅 Vega Planning API")
  //        .pathsToMatch("/vega/**")
  //        .build();
  //  }
}
