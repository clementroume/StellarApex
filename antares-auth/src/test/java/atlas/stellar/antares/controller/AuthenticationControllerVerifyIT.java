package atlas.stellar.antares.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import atlas.stellar.antares.config.BaseIntegrationTest;
import atlas.stellar.antares.dto.AuthenticationRequest;
import atlas.stellar.antares.dto.RegisterRequest;
import atlas.stellar.antares.model.Role;
import atlas.stellar.antares.model.User;
import atlas.stellar.antares.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Integration tests specifically for the Forward Auth endpoint (/verify). */
class AuthenticationControllerVerifyIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    // Clean slate before each test to avoid conflicts
    userRepository.deleteAll();
  }

  @AfterEach
  void cleanUpRedis(@Autowired StringRedisTemplate redisTemplate) {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  @Test
  @DisplayName("Verify: Unauthenticated request should redirect to login with returnUrl")
  void testVerify_whenUnauthenticated_shouldRedirectToLogin() throws Exception {
    // Given: A request coming from Traefik (simulated headers)
    mockMvc
        .perform(
            get("/antares/auth/verify")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "admin.stellar.atlas")
                .header("X-Forwarded-Uri", "/dashboard"))
        // Then: Expect 302 Found (Redirect)
        .andExpect(status().isFound())
        // Verify the Location header contains the correctly encoded returnUrl
        .andExpect(
            header()
                .string(
                    "Location",
                    "https://stellar.atlas/auth/login?returnUrl=https%3A%2F%2Fadmin.stellar.atlas%2Fdashboard"));
  }

  @Test
  @DisplayName("Verify: Authenticated USER (not admin) should be forbidden (403)")
  void testVerify_whenUserRole_shouldReturnForbidden() throws Exception {
    // 1. Create and Login a standard USER
    Cookie[] userCookies = registerAndLogin();

    // 2. Perform verification
    mockMvc
        .perform(get("/antares/auth/verify").cookie(userCookies))
        // Then: Expect 403 Forbidden (Access Denied)
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Verify: Authenticated ADMIN should be allowed (200)")
  void testVerify_whenAdminRole_shouldReturnOk() throws Exception {
    // 1. Create an ADMIN manually (register endpoint only creates USERS)
    createAdminInDb();

    // 2. Login to get cookies
    Cookie[] adminCookies = login("admin@test.com", "adminPass123");

    // 3. Perform verification
    mockMvc
        .perform(get("/antares/auth/verify").cookie(adminCookies))
        // Then: Expect 200 OK (Access Granted)
        .andExpect(status().isOk());
  }

  // --- Helpers ---

  private Cookie[] registerAndLogin() throws Exception {
    RegisterRequest registerRequest =
        new RegisterRequest("Test", "User", "user@test.com", "password123");
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());

    return login("user@test.com", "password123");
  }

  private void createAdminInDb() {
    userRepository.save(
        User.builder()
            .firstName("Admin")
            .lastName("User")
            .email("admin@test.com")
            .password(passwordEncoder.encode("adminPass123"))
            .role(Role.ROLE_ADMIN)
            .build());
  }

  private Cookie[] login(String email, String password) throws Exception {
    AuthenticationRequest loginRequest = new AuthenticationRequest(email, password);
    MvcResult result =
        mockMvc
            .perform(
                post("/antares/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();
    return result.getResponse().getCookies();
  }
}
