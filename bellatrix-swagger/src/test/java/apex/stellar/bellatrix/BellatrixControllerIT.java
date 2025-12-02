package apex.stellar.bellatrix;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link BellatrixController}.
 *
 * <p>Uses WireMock to simulate Antares and Aldebaran backend services, ensuring that the proxy
 * controller correctly forwards requests and returns valid OpenAPI specifications.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WireMockTest(httpPort = 18080) // Mock server for Antares
class BellatrixControllerIT {

  @Autowired private MockMvc mockMvc;

  /**
   * Configures dynamic properties to point to WireMock servers instead of real services.
   *
   * @param registry the dynamic property registry
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // Point both services to the WireMock instance
    registry.add("swagger.services.antares.url", () -> "http://localhost:18080");
    registry.add("swagger.services.aldebaran.url", () -> "http://localhost:18080");
  }

  @BeforeEach
  void setupMocks() {
    // Mock Antares OpenAPI response
    stubFor(
        WireMock.get(urlEqualTo("/antares-docs"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "openapi": "3.0.1",
                          "info": {
                            "title": "Antares Auth API",
                            "description": "Authentication & User Management",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/antares/auth/login": {
                              "post": {
                                "summary": "Authenticate user"
                              }
                            }
                          }
                        }
                        """)));

    // Mock Aldebaran OpenAPI response
    stubFor(
        WireMock.get(urlEqualTo("/aldebaran-docs"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "openapi": "3.0.1",
                          "info": {
                            "title": "Aldebaran Training API",
                            "description": "Workout Management & Performance",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/aldebaran/workouts": {
                              "get": {
                                "summary": "List workouts"
                              }
                            }
                          }
                        }
                        """)));
  }

  @Test
  @DisplayName("GET /antares-docs should return valid JSON from proxied service")
  void testGetAntaresApiDocs_shouldReturnJson() throws Exception {
    mockMvc
        .perform(get("/antares-docs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName("GET /antares-docs should return OpenAPI specification with required fields")
  void testGetAntaresApiDocs_shouldContainOpenApiFields() throws Exception {
    mockMvc
        .perform(get("/antares-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.0.1"))
        .andExpect(jsonPath("$.info.title").value("Antares Auth API"))
        .andExpect(jsonPath("$.paths").exists())
        .andExpect(jsonPath("$.paths['/antares/auth/login']").exists());
  }

  @Test
  @DisplayName("GET /aldebaran-docs should return valid JSON from proxied service")
  void testGetAldebaranApiDocs_shouldReturnJson() throws Exception {
    mockMvc
        .perform(get("/aldebaran-docs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName("GET /aldebaran-docs should return OpenAPI specification with required fields")
  void testGetAldebaranApiDocs_shouldContainOpenApiFields() throws Exception {
    mockMvc
        .perform(get("/aldebaran-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.0.1"))
        .andExpect(jsonPath("$.info.title").value("Aldebaran Training API"))
        .andExpect(jsonPath("$.paths").exists())
        .andExpect(jsonPath("$.paths['/aldebaran/workouts']").exists());
  }

  @Test
  @DisplayName("Swagger UI root path should be accessible and redirect correctly")
  void testSwaggerUiRootPath_shouldBeAccessible() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().is3xxRedirection()); // Swagger UI redirects to /swagger-ui/index.html
  }

  @Test
  @DisplayName("Proxy should handle backend service errors gracefully")
  void testProxyErrorHandling_shouldReturn500OnBackendFailure() throws Exception {
    // Override mock to simulate backend failure
    stubFor(
        WireMock.get(urlEqualTo("/antares-docs"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    mockMvc.perform(get("/antares-docs")).andExpect(status().is5xxServerError());
  }
}
