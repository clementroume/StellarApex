package com.antares.api;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.antares.api.config.BaseIntegrationTest;
import com.antares.api.controller.AuthenticationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test class for verifying the basic functionality of the Antares API.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 */
class AntaresApiIT extends BaseIntegrationTest {

  @Autowired private AuthenticationController authenticationController;

  /**
   * Verifies that the Spring application context loads successfully.
   *
   * <p>Given: The Spring application context is bootstrapped When: The test is executed Then: The
   * AuthenticationController should be properly autowired and not null
   */
  @Test
  @DisplayName("Application context should load successfully")
  void contextLoads() {
    assertThat(authenticationController).isNotNull();
  }
}
