package apex.stellar.antares.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymResponse;
import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Integration tests specifically for the Forward Auth endpoint (/verify). */
class AuthenticationControllerVerifyIT extends BaseIntegrationTest {

  @Autowired private GymRepository gymRepository;
  @Autowired private MembershipRepository membershipRepository;

  private Gym testGym;

  @BeforeEach
  void setUp() {
    // Deletion order is important due to FK constraints
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    // Create a reference Gym
    testGym =
        gymRepository.save(
            Gym.builder()
                .name("Test Gym")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("123456")
                .isAutoSubscription(true)
                .build());
  }

  // ==================================================================================
  // 1. ADMIN / INFRASTRUCTURE FLOW (Target: /verify/admin)
  // ==================================================================================

  @Test
  @DisplayName("Verify: Unauthenticated request should redirect to login with returnUrl")
  void testVerify_whenUnauthenticated_shouldRedirectToLogin() throws Exception {
    // When / Then
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
    // Given
    Cookie[] userCookies = registerAndLogin("user@test.com", "password123");

    // When / Then
    mockMvc
        .perform(get("/antares/auth/verify/admin").cookie(userCookies))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Verify: Authenticated ADMIN should be allowed (200)")
  void testVerify_whenAdminRole_shouldReturnOk() throws Exception {
    // Given
    createAdmin("admin@test.com", "adminPass123");
    Cookie[] adminCookies = login("admin@test.com", "adminPass123");

    // When / Then
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
    // When / Then
    mockMvc.perform(get("/antares/auth/verify/api")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Verify API: Authenticated ATHLETE should return OK (200) and headers")
  void testVerifyApi_whenAuthenticated_shouldReturnHeaders() throws Exception {
    // Given
    Cookie[] userCookies = registerAndLogin("athlete@test.com", "password123");

    // When / Then
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
    // Given: User with Membership
    Cookie[] cookies = registerLoginAndJoin("gymuser@test.com", testGym);

    // When: Perform verification with Gym Context Header (Dynamic ID)
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
    // Given
    Cookie[] cookies = registerLoginAndJoin("gymuser2@test.com", testGym);

    // When / Then
    mockMvc
        .perform(
            get("/antares/auth/verify/api")
                .cookie(cookies)
                .header("X-Context-Gym-Id", "999999")) // Non-existent ID or not a member
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "Verify API: Authenticated USER without Context should return Independent User info (No Gym ID)")
  void testVerifyApi_noContext_shouldReturnUserContextOnly() throws Exception {
    // Given: User with Membership (linked to testGym created in setUp)
    Cookie[] cookies = registerLoginAndJoin("gymuser3@test.com", testGym);

    // When: Perform verification WITHOUT the X-Context-Gym-Id header
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Auth-User-Id"))
        .andExpect(header().doesNotExist("X-Auth-Gym-Id"));
  }

  @Test
  @DisplayName("Verify API: Authenticated with PENDING Membership should return Forbidden (403)")
  void testVerifyApi_withPendingMembership_shouldReturnForbidden() throws Exception {
    // Given
    // 1. Join via API (results in ACTIVE because testGym is: auto-sub=true)
    Cookie[] cookies = registerLoginAndJoin("pending@test.com", testGym);

    // 2. Manually downgrade status to PENDING via Repo to simulate the edge case
    Membership m =
        membershipRepository
            .findByUserIdAndGymId(
                userRepository.findByEmail("pending@test.com").orElseThrow().getId(),
                testGym.getId())
            .orElseThrow();
    m.setStatus(MembershipStatus.PENDING);
    membershipRepository.save(m);

    // When / Then
    mockMvc
        .perform(
            get("/antares/auth/verify/api")
                .cookie(cookies)
                .header("X-Context-Gym-Id", testGym.getId().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Verify API: Athlete accessing PENDING Gym should return Forbidden (403)")
  void testVerifyApi_PendingGym_Athlete_shouldReturnForbidden() throws Exception {
    // Given
    // 1. Create a Pending Gym via Repo (Infrastructure setup)
    Gym pendingGym =
        gymRepository.save(
            Gym.builder()
                .name("Pending Gym")
                .status(GymStatus.PENDING_APPROVAL) // <--- PENDING GYM
                .enrollmentCode("PEND")
                .isAutoSubscription(
                    true) // Ensure joining gives ACTIVE membership to isolate Gym Status check
                .build());

    // 2. User joins via API
    Cookie[] cookies = registerLoginAndJoin("athlete_p@test.com", pendingGym);

    // When / Then
    mockMvc
        .perform(
            get("/antares/auth/verify/api")
                .cookie(cookies)
                .header("X-Context-Gym-Id", pendingGym.getId().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Verify API: Owner accessing PENDING Gym should return OK (200)")
  void testVerifyApi_PendingGym_Owner_shouldReturnOk() throws Exception {
    // Given
    // 1. Register & Login
    Cookie[] cookies = registerAndLogin("owner_p@test.com", "password123");

    // 2. Create Gym via API (Automatically makes user OWNER of a PENDING gym)
    GymRequest gymRequest =
        new GymRequest("Pending Gym Owner", "Desc", false, "gym-creation-secret");
    MvcResult result =
        mockMvc
            .perform(
                post("/antares/gyms")
                    .cookie(cookies)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(gymRequest)))
            .andExpect(status().isCreated())
            .andReturn();
    GymResponse gymResponse =
        jsonMapper.readValue(result.getResponse().getContentAsString(), GymResponse.class);

    // When / Then
    mockMvc
        .perform(
            get("/antares/auth/verify/api")
                .cookie(cookies)
                .header("X-Context-Gym-Id", gymResponse.id().toString()))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Role", "OWNER"));
  }

  @Test
  @DisplayName("Verify API: Invalid Gym ID format should return Bad Request (400)")
  void testVerifyApi_InvalidGymIdFormat_shouldReturnBadRequest() throws Exception {
    // Given: Just a logged-in user
    Cookie[] cookies = registerAndLogin("badrequest@test.com", "password123");

    // When / Then
    mockMvc
        .perform(
            get("/antares/auth/verify/api")
                .cookie(cookies)
                .header("X-Context-Gym-Id", "invalid-id")) // <--- String instead of Long
        .andExpect(status().isBadRequest());
  }

  // --- Helpers ---
  private Cookie[] registerLoginAndJoin(String email, Gym gym) throws Exception {
    Cookie[] cookies = registerAndLogin(email, "password123");

    JoinGymRequest joinRequest = new JoinGymRequest(gym.getId(), gym.getEnrollmentCode());
    mockMvc
        .perform(
            post("/antares/gyms/join")
                .cookie(cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(joinRequest)))
        .andExpect(status().isOk());

    return cookies;
  }
}
