package apex.stellar.antares.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import apex.stellar.antares.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
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
  @Autowired private GymRepository gymRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StringRedisTemplate redisTemplate;

  private Gym testGym;

  @BeforeEach
  void setUp() {
    // Ordre de suppression important pour les FK
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    // Création d'une Gym de référence
    testGym =
        gymRepository.save(
            Gym.builder()
                .name("Test Gym")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("123456")
                .isAutoSubscription(true)
                .build());
  }

  @AfterEach
  void cleanUpRedis() {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  // ==================================================================================
  // 1. ADMIN / INFRASTRUCTURE FLOW (Target: /verify/admin)
  // ==================================================================================

  @Test
  @DisplayName("Verify: Unauthenticated request should redirect to login with returnUrl")
  void testVerify_whenUnauthenticated_shouldRedirectToLogin() throws Exception {
    mockMvc
        .perform(
            get("/antares/auth/verify/admin")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "admin.stellar.atlas")
                .header("X-Forwarded-Uri", "/dashboard"))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    "Location",
                    "https://stellar.apex/auth/login?returnUrl=https%3A%2F%2Fadmin.stellar.atlas%2Fdashboard"));
  }

  @Test
  @DisplayName("Verify: Authenticated USER (not admin) should be forbidden (403)")
  void testVerify_whenUserRole_shouldReturnForbidden() throws Exception {
    Cookie[] userCookies = registerAndLogin();

    mockMvc
        .perform(get("/antares/auth/verify/admin").cookie(userCookies))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Verify: Authenticated ADMIN should be allowed (200)")
  void testVerify_whenAdminRole_shouldReturnOk() throws Exception {
    createAdminInDb();
    Cookie[] adminCookies = login("admin@test.com", "adminPass123");

    mockMvc
        .perform(get("/antares/auth/verify/admin").cookie(adminCookies))
        .andExpect(status().isOk());
  }

  // ==================================================================================
  // 2. API / MICROSERVICE FLOW (Target: /verify/api)
  // ==================================================================================

  @Test
  @DisplayName("Verify API: Unauthenticated request should return Unauthorized (401)")
  void testVerifyApi_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
    mockMvc.perform(get("/antares/auth/verify/api")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Verify API: Authenticated ATHLETE should return OK (200) and headers")
  void testVerifyApi_whenAuthenticated_shouldReturnHeaders() throws Exception {
    Cookie[] userCookies = registerAndLogin();

    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(userCookies))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Auth-User-Id"))
        .andExpect(header().string("X-Auth-User-Role", "USER"))
        .andExpect(header().string("X-Auth-User-Locale", "en"));
  }

  @Test
  @DisplayName("Verify API: Authenticated ATHLETE with Gym Context should return Gym headers")
  void testVerifyApi_withGymContext_shouldReturnGymHeaders() throws Exception {
    // 1. Create User with Membership
    createUserWithMembership("gymuser@test.com");
    Cookie[] cookies = login("gymuser@test.com", "password123");

    // 2. Perform verification with Gym Context Header (ID dynamique)
    mockMvc
        .perform(
            get("/antares/auth/verify/api")
                .cookie(cookies)
                .header("X-Context-Gym-Id", testGym.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-Gym-Id", testGym.getId().toString()))
        .andExpect(header().string("X-Auth-User-Role", "ATHLETE"))
        .andExpect(header().exists("X-Auth-User-Permissions"));
  }

  @Test
  @DisplayName(
      "Verify API: Authenticated ATHLETE with Invalid Gym Context should return Forbidden (403)")
  void testVerifyApi_withInvalidGymContext_shouldReturnForbidden() throws Exception {
    createUserWithMembership("gymuser2@test.com");
    Cookie[] cookies = login("gymuser2@test.com", "password123");

    mockMvc
        .perform(
            get("/antares/auth/verify/api")
                .cookie(cookies)
                .header("X-Context-Gym-Id", "999999")) // ID inexistant ou non membre
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "Verify API: Authenticated USER without Context should return Independent User info (No Gym ID)")
  void testVerifyApi_noContext_shouldReturnUserContextOnly() throws Exception {
    // 1. Create User with Membership (linked to testGym created in setUp)
    createUserWithMembership("gymuser3@test.com");
    Cookie[] cookies = login("gymuser3@test.com", "password123");

    // 2. Perform verification WITHOUT the X-Context-Gym-Id header
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Auth-User-Id"))
        .andExpect(header().doesNotExist("X-Auth-Gym-Id"));
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
            .platformRole(PlatformRole.ADMIN)
            .build());
  }

  private void createUserWithMembership(String email) {
    User user =
        userRepository.save(
            User.builder()
                .firstName("Gym")
                .lastName("User")
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .platformRole(PlatformRole.USER)
                .build());

    // IMPORTANT : On sauvegarde le Membership correctement lié
    membershipRepository.save(
        Membership.builder()
            .user(user)
            .gym(testGym) // Utilisation de la Gym créée dans setUp()
            .gymRole(GymRole.ATHLETE)
            .status(MembershipStatus.ACTIVE)
            .permissions(Collections.emptySet())
            .build());
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
