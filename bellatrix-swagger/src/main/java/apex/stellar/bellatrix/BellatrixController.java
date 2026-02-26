package apex.stellar.bellatrix;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Proxy Controller for OpenAPI documentation.
 *
 * <p>This controller serves as a pass-through. It retrieves raw OpenAPI JSON from internal
 * microservices and returns it to the browser for display in Swagger UI. This avoids CORS issues
 * and prevents direct exposure of internal ports.
 */
@RestController
@RequiredArgsConstructor
public class BellatrixController {

  private final RestClient restClient = RestClient.create();

  @Value("${swagger.services.antares.url}")
  private String antaresUrl;

  @Value("${swagger.services.aldebaran.url}")
  private String aldebaranUrl;

  /**
   * Proxy for the authentication service (Antares) documentation. Calls the internal endpoint
   * /antares-docs.
   */
  @GetMapping(value = "/antares-docs", produces = MediaType.APPLICATION_JSON_VALUE)
  public String getAntaresApiDocs() {
    return restClient.get().uri(antaresUrl + "/antares-docs").retrieve().body(String.class);
  }

  /**
   * Proxy for the training service (Aldebaran) documentation. Calls the internal endpoint
   * /aldebaran-docs.
   */
  @GetMapping(value = "/aldebaran-docs", produces = MediaType.APPLICATION_JSON_VALUE)
  public String getAldebaranApiDocs() {
    return restClient.get().uri(aldebaranUrl + "/aldebaran-docs").retrieve().body(String.class);
  }
}
