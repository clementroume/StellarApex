 package apex.stellar.antares.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.model.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImpersonationControllerIT extends BaseIntegrationTest {


  private User targetUser;

  @BeforeEach
  void setUp() throws Exception {
    userRepository.deleteAll();

    // 1. Legitimate Admin
    createAdmin("admin@stellar.com", "password1234");

    // 2. Target User
    register("target@stellar.com", "password1234");
    targetUser = userRepository.findByEmail("target@stellar.com").orElseThrow();

    // 3. Malicious User (even Owner)
    register("hacker@stellar.com", "password1234");
  }

  @Test
  @DisplayName("Impersonate: Global Admin can impersonate anyone")
  void testImpersonate_AsAdmin_Success() throws Exception {
    // Given
    Cookie[] adminCookies = login("admin@stellar.com", "password1234");

    // When / Then
    mockMvc
        .perform(
            post("/antares/auth/impersonate/" + targetUser.getId())
                .cookie(adminCookies)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("stellar_access_token"))
        .andExpect(cookie().exists("stellar_refresh_token"))
        .andExpect(jsonPath("$.email").value("target@stellar.com"));
  }

  @Test
  @DisplayName("Impersonate: Regular User (even Owner) CANNOT impersonate")
  void testImpersonate_AsNonAdmin_Forbidden() throws Exception {
    // Given
    Cookie[] hackerCookies = login("hacker@stellar.com", "password1234");

    // When / Then
    mockMvc
        .perform(
            post("/antares/auth/impersonate/" + targetUser.getId())
                .cookie(hackerCookies)
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Impersonate: Cannot impersonate non-existent user")
  void testImpersonate_NotFound() throws Exception {
    // Given
    Cookie[] adminCookies = login("admin@stellar.com", "password1234");

    // When / Then
    mockMvc
        .perform(post("/antares/auth/impersonate/99999").cookie(adminCookies).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Impersonate: Unauthenticated request should be Forbidden")
  void testImpersonate_Unauthenticated() throws Exception {
    // When / Then
    mockMvc
        .perform(post("/antares/auth/impersonate/" + targetUser.getId()).with(csrf()))
        .andExpect(status().isForbidden());
  }
}
