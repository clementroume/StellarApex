package apex.stellar.aldebaran.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityIT extends BaseIntegrationTest {

  @Test
  @DisplayName("Should reject request without internal secret (403)")
  void shouldRejectRequestWithoutInternalSecret() throws Exception {
    // Even with valid Auth headers, missing secret should fail
    mockMvc
        .perform(
            get("/aldebaran/muscles")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Should accept request with valid internal secret (200)")
  void shouldAcceptRequestWithInternalSecret() throws Exception {
    // Valid secret + Valid Auth headers -> OK
    mockMvc
        .perform(
            get("/aldebaran/muscles")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk());
  }
}
