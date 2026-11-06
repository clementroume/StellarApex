package apex.stellar.antares.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The demo access endpoint is public and answers 404 when no demo password is configured. */
class DemoAccessIT extends BaseIntegrationTest {

  @Test
  @DisplayName("demo access: public, 404 when no demo password is configured (test profile)")
  void demoAccess_IsNotFoundWithoutConfiguration() throws Exception {
    mockMvc.perform(get("/antares/auth/demo")).andExpect(status().isNotFound());
  }
}
