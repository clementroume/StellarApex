package com.antares.api;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.antares.api.config.BaseIntegrationTest;
import com.antares.api.controller.AuthenticationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Integration test class to verify the Spring application context loading. */
class AntaresApiIT extends BaseIntegrationTest {

  @Autowired private AuthenticationController authenticationController;

  /** Verifies that the Spring application context loads successfully. */
  @Test
  @DisplayName("Application context should load successfully")
  void contextLoads() {
    assertThat(authenticationController).isNotNull();
  }
}
