package apex.stellar.antares.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

class ImpersonationControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;

  private User adminUser;
  private User targetUser;
  private User hackerUser;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    // 1. Admin légitime
    adminUser =
        userRepository.save(
            User.builder()
                .email("admin@stellar.com")
                .password("pass")
                .platformRole(PlatformRole.ADMIN)
                .build());

    // 2. Utilisateur cible
    targetUser =
        userRepository.save(
            User.builder()
                .email("target@stellar.com")
                .password("pass")
                .platformRole(PlatformRole.USER)
                .build());

    // 3. Utilisateur malveillant (même Owner)
    hackerUser =
        userRepository.save(
            User.builder()
                .email("hacker@stellar.com")
                .password("pass")
                .platformRole(PlatformRole.USER) // Owner is a GymRole, here we set PlatformRole
                .build());
  }

  private UsernamePasswordAuthenticationToken authenticate(User user) {
    return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
  }

  @Test
  @DisplayName("Impersonate: Global Admin can impersonate anyone")
  void testImpersonate_AsAdmin_Success() throws Exception {
    mockMvc
        .perform(
            post("/antares/auth/impersonate/" + targetUser.getId())
                .with(authentication(authenticate(adminUser)))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("stellar_access_token"))
        .andExpect(cookie().exists("stellar_refresh_token"))
        .andExpect(jsonPath("$.email").value("target@stellar.com"));
  }

  @Test
  @DisplayName("Impersonate: Regular User (even Owner) CANNOT impersonate")
  void testImpersonate_AsNonAdmin_Forbidden() throws Exception {
    mockMvc
        .perform(
            post("/antares/auth/impersonate/" + targetUser.getId())
                .with(authentication(authenticate(hackerUser)))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Impersonate: Cannot impersonate non-existent user")
  void testImpersonate_NotFound() throws Exception {
    mockMvc
        .perform(
            post("/antares/auth/impersonate/99999")
                .with(authentication(authenticate(adminUser)))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }
}
