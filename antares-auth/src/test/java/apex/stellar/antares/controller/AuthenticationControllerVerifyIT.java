package apex.stellar.antares.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Role;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

/** Integration tests specifically for the Forward Auth endpoint (/verify). */
class AuthenticationControllerVerifyIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
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

  // ==================================================================================
  // 1. ADMIN / INFRASTRUCTURE FLOW (Target: /verify/admin)
  // Behavior: Browser-based, expects Redirects (302) or Forbidden (403).
  // ==================================================================================

  @Test
  @DisplayName("Verify: Unauthenticated request should redirect to login with returnUrl")
  void testVerify_whenUnauthenticated_shouldRedirectToLogin() throws Exception {
    // Given: A request coming from Traefik (simulated headers)
    mockMvc
        .perform(
            get("/antares/auth/verify/admin")
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
                    "https://stellar.apex/auth/login?returnUrl=https%3A%2F%2Fadmin.stellar.atlas%2Fdashboard"));
  }

  @Test
  @DisplayName("Verify: Authenticated USER (not admin) should be forbidden (403)")
  void testVerify_whenUserRole_shouldReturnForbidden() throws Exception {
    // 1. Create and Login a standard USER
    Cookie[] userCookies = registerAndLogin();

    // 2. Perform verification
    mockMvc
        .perform(get("/antares/auth/verify/admin").cookie(userCookies))
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
        .perform(get("/antares/auth/verify/admin").cookie(adminCookies))
        // Then: Expect 200 OK (Access Granted)
        .andExpect(status().isOk());
  }

  // ==================================================================================
  // 2. API / MICROSERVICE FLOW (Target: /verify/api)
  // Behavior: XHR/AJAX based, expects Unauthorized (401) or OK (200) + Headers.
  // ==================================================================================

  @Test
  @DisplayName("Verify API: Unauthenticated request should return Unauthorized (401)")
  void testVerifyApi_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
    mockMvc
        .perform(get("/antares/auth/verify/api"))
        // Then: Expect 401 (Triggers frontend interceptor refresh flow)
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Verify API: Authenticated USER should return OK (200) and X-Auth headers")
  void testVerifyApi_whenAuthenticated_shouldReturnHeaders() throws Exception {
    Cookie[] userCookies = registerAndLogin();

    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(userCookies))
        // Then: Expect 200 OK
        .andExpect(status().isOk())
        // And: Check specific Forward Auth headers needed by Aldebaran
        .andExpect(header().exists("X-Auth-User-Id"))
        .andExpect(header().string("X-Auth-User-Role", "ROLE_USER"))
        .andExpect(header().string("X-Auth-User-Locale", "en"));
  }

  @Test
  @DisplayName("Verify API: Authenticated USER with Gym Context should return Gym headers")
  void testVerifyApi_withGymContext_shouldReturnGymHeaders() throws Exception {
    // 1. Create User with Membership
    createUserWithMembership("gymuser@test.com");
    Cookie[] cookies = login("gymuser@test.com", "password123");

    // 2. Perform verification with Gym Context Header
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies).header("X-Context-Gym-Id", "100"))
        // Then: Expect 200 OK
        .andExpect(status().isOk())
        // And: Check Gym-specific headers
        .andExpect(header().string("X-Auth-Gym-Id", "100"))
        .andExpect(header().string("X-Auth-User-Role", "ROLE_USER"))
        .andExpect(header().exists("X-Auth-User-Permissions"));
  }

  @Test
  @DisplayName(
      "Verify API: Authenticated USER with Invalid Gym Context should return Forbidden (403)")
  void testVerifyApi_withInvalidGymContext_shouldReturnForbidden() throws Exception {
    // 1. Create User with Membership for Gym 100
    createUserWithMembership("gymuser2@test.com");
    Cookie[] cookies = login("gymuser2@test.com", "password123");

    // 2. Perform verification with WRONG Gym Context Header
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies).header("X-Context-Gym-Id", "999"))
        // Then: Expect 403 Forbidden
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Verify API: Authenticated USER without Context should default to first Membership")
  void testVerifyApi_noContext_shouldDefaultToMembership() throws Exception {
    // 1. Create User with Membership for Gym 100
    createUserWithMembership("gymuser3@test.com");
    Cookie[] cookies = login("gymuser3@test.com", "password123");

    // 2. Perform verification WITHOUT a header
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        // Then: Expect 200 OK
        .andExpect(status().isOk())
        // And: Should default to the membership
        .andExpect(header().string("X-Auth-Gym-Id", "100"));
  }

  // --- Helpers ---

  private Cookie[] registerAndLogin() throws Exception {
    RegisterRequest registerRequest =
        new RegisterRequest("Test", "User", "user@test.com", "password123");
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(registerRequest)))
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

  private void createUserWithMembership(String email) {
    User user =
        User.builder()
            .firstName("Gym")
            .lastName("User")
            .email(email)
            .password(passwordEncoder.encode("password123"))
            .role(Role.ROLE_USER)
            .build();

    Membership membership =
        Membership.builder()
            .gymId(100L)
            .role(Role.ROLE_USER)
            .permissions(Collections.emptySet())
            .user(user)
            .build();

    user.setMemberships(List.of(membership));
    userRepository.save(user);
  }

  private Cookie[] login(String email, String password) throws Exception {
    AuthenticationRequest loginRequest = new AuthenticationRequest(email, password);
    MvcResult result =
        mockMvc
            .perform(
                post("/antares/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();
    return result.getResponse().getCookies();
  }
}
