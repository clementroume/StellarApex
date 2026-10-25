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
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
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
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Login", "auser"))
        .andExpect(header().string("X-Auth-User-Name", "Admin User"))
        .andExpect(header().string("X-Auth-User-Email", "admin@test.com"))
        .andExpect(header().string("X-Auth-User-Role", "Admin"));
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
  @DisplayName("Verify API: Member should receive its memberships (no Gym context needed)")
  void testVerifyApi_withMembership_shouldReturnMemberships() throws Exception {
    // Given: User with Membership
    Cookie[] cookies = registerLoginAndJoin("gymuser@test.com", testGym);

    // When / Then
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Auth-User-Id"))
        .andExpect(header().string("X-Auth-User-Role", "USER"))
        .andExpect(header().string("X-Auth-Memberships", testGym.getId() + ":ATHLETE:"))
        .andExpect(header().doesNotExist("X-Auth-Gym-Id"))
        .andExpect(header().doesNotExist("X-Auth-User-Permissions"))
        .andExpect(header().doesNotExist("X-Auth-Actor-Id"));
  }

  @Test
  @DisplayName("Verify API: User without membership should receive empty memberships")
  void testVerifyApi_noMembership_shouldReturnEmptyMemberships() throws Exception {
    // Given
    Cookie[] cookies = registerAndLogin("independent@test.com", "password123");

    // When / Then
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-Memberships", ""));
  }

  @Test
  @DisplayName("Verify API: PENDING Membership should be excluded from memberships")
  void testVerifyApi_withPendingMembership_shouldBeExcluded() throws Exception {
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
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-Memberships", ""));
  }

  @Test
  @DisplayName("Verify API: Athlete of a PENDING Gym should be excluded from memberships")
  void testVerifyApi_PendingGym_Athlete_shouldBeExcluded() throws Exception {
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

    // 2. User joins the pending gym and the active one
    Cookie[] cookies = registerLoginAndJoin("athlete_p@test.com", pendingGym);
    join(cookies, testGym);

    // When / Then
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-Memberships", testGym.getId() + ":ATHLETE:"));
  }

  @Test
  @DisplayName("Verify API: Owner of a PENDING Gym should get it with every permission")
  void testVerifyApi_PendingGym_Owner_shouldBeIncluded() throws Exception {
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
                    .header("X-XSRF-TOKEN", getXsrfToken(cookies))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(gymRequest)))
            .andExpect(status().isCreated())
            .andReturn();
    GymResponse gymResponse =
        jsonMapper.readValue(result.getResponse().getContentAsString(), GymResponse.class);

    // When / Then
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Role", "USER"))
        .andExpect(
            header()
                .string(
                    "X-Auth-Memberships",
                    gymResponse.id()
                        + ":OWNER:WOD_WRITE+SCORE_VERIFY+MANAGE_MEMBERSHIPS+MANAGE_SETTINGS"));
  }

  @Test
  @DisplayName("Verify API: ADMIN member should keep ADMIN platform role")
  void testVerifyApi_AdminMember_shouldKeepAdminRole() throws Exception {
    // Given
    createAdmin("admin.member@test.com", "adminPass123");
    Cookie[] cookies = login("admin.member@test.com", "adminPass123");
    join(cookies, testGym);

    // When / Then
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Role", "ADMIN"))
        .andExpect(header().string("X-Auth-Memberships", testGym.getId() + ":ATHLETE:"));
  }

  // --- Helpers ---
  private Cookie[] registerLoginAndJoin(String email, Gym gym) throws Exception {
    Cookie[] cookies = registerAndLogin(email, "password123");
    join(cookies, gym);
    return cookies;
  }

  private void join(Cookie[] cookies, Gym gym) throws Exception {
    String csrf = getXsrfToken(cookies);

    JoinGymRequest joinRequest = new JoinGymRequest(gym.getId(), gym.getEnrollmentCode());
    mockMvc
        .perform(
            post("/antares/gyms/join")
                .cookie(cookies)
                .header("X-XSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(joinRequest)))
        .andExpect(status().isOk());
  }
}
