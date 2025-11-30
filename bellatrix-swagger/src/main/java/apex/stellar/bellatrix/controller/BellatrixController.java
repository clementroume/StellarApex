package apex.stellar.bellatrix.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller that proxies OpenAPI specifications from backend microservices.
 *
 * <p>This controller fetches the OpenAPI JSON from each service and optionally modifies the
 * 'servers' field to ensure correct URL resolution in Swagger UI.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BellatrixController {

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${swagger.services.antares.url}")
  private String antaresUrl;

  @Value("${swagger.services.orion.url}")
  private String aldebaranUrl;

  @Value("${swagger.gateway.public-url}")
  private String publicUrl;

  /** Proxies the OpenAPI spec from Antares Auth service. */
  @GetMapping(value = "/v3/api-docs/antares", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<@NonNull String> getAntaresApiDocs() {
    log.debug("Fetching Antares API docs from: {}", antaresUrl);
    return fetchAndModifyApiDocs(antaresUrl + "/v3/api-docs", "/antares");
  }

  /** Proxies the OpenAPI spec from Orion Training service. */
  @GetMapping(value = "/v3/api-docs/aldebaran", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<@NonNull String> getAldebaranApiDocs() {
    log.debug("Fetching Orion API docs from: {}", aldebaranUrl);
    return fetchAndModifyApiDocs(aldebaranUrl + "/v3/api-docs", "/aldebaran");
  }

  /**
   * Fetches OpenAPI JSON from a service and modifies the 'servers' array.
   *
   * @param url The internal service URL
   * @param basePath The public base path (e.g., /antares)
   * @return ResponseEntity with the modified OpenAPI JSON
   */
  private ResponseEntity<@NonNull String> fetchAndModifyApiDocs(String url, String basePath) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("Failed to fetch API docs from {}: HTTP {}", url, response.statusCode());
        return ResponseEntity.status(response.statusCode()).body("{}");
      }

      // Parse and modify the JSON
      String modifiedJson = modifyServersField(response.body(), basePath);

      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(modifiedJson);

    } catch (IOException | InterruptedException e) {
      log.error("Error fetching API docs from {}", url, e);
      Thread.currentThread().interrupt();
      return ResponseEntity.internalServerError().body("{}");
    }
  }

  /**
   * Modifies the 'servers' field in the OpenAPI JSON to use the public gateway URL.
   *
   * @param jsonBody The original OpenAPI JSON
   * @param basePath The base path to append (e.g., /antares)
   * @return The modified JSON string
   */
  private String modifyServersField(String jsonBody, String basePath) {
    try {
      JsonNode root = objectMapper.readTree(jsonBody);

      if (root instanceof ObjectNode objectNode) {
        // Create a new servers array
        var serversArray = objectMapper.createArrayNode();
        var serverNode = objectMapper.createObjectNode();
        serverNode.put("url", publicUrl + basePath);
        serverNode.put("description", "StellarApex Gateway");
        serversArray.add(serverNode);

        // Replace the servers field
        objectNode.set("servers", serversArray);

        return objectMapper.writeValueAsString(objectNode);
      }

      return jsonBody;

    } catch (IOException e) {
      log.error("Error modifying OpenAPI JSON", e);
      return jsonBody;
    }
  }
}
