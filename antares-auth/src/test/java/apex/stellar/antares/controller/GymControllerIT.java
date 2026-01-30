package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymSettingsRequest;
import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class GymControllerIT extends BaseIntegrationTest {

  @Autowired private GymRepository gymRepository;
  @Autowired private MembershipRepository membershipRepository;

  private User testUser;
  private Gym activeGym;
  private Cookie[] authCookies;

  @BeforeEach
  void setUp() throws Exception {
    // 1. Clean database to ensure no ID conflicts or FK violations
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    // 2. Create User via API (Real flow)
    authCookies = registerAndLogin("athlete@test.com", "password1234");
    testUser = userRepository.findByEmail("athlete@test.com").orElseThrow();

    // 3. Create and persist a fresh Gym.
    activeGym =
        gymRepository.save(
            Gym.builder()
                .name("Active Gym")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("CODE123")
                .isAutoSubscription(false)
                .build());
  }

  @Test
  @DisplayName("Create Gym: Should succeed with valid token and assign OWNER role")
  void testCreateGym_Success() throws Exception {
    // Given
    GymRequest request = new GymRequest("Iron Box", "Hardcore gym", false, "gym-creation-secret");

    // When
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Iron Box"))
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

    // Then: Verify Database state
    Gym gym = gymRepository.findByName("Iron Box").orElseThrow();
    assertThat(gym.getStatus()).isEqualTo(GymStatus.PENDING_APPROVAL);

    Membership membership =
        membershipRepository.findByUserIdAndGymId(testUser.getId(), gym.getId()).orElseThrow();
    assertThat(membership.getGymRole()).isEqualTo(GymRole.OWNER);
  }

  @Test
  @DisplayName("Create Gym: Should succeed (Programming -> PROGRAMMER role)")
  void testCreateGym_AsProgrammer_Success() throws Exception {
    // Given: isProgramming = true to verify ROLE_PROGRAMMER
    GymRequest request = new GymRequest("Zeus Prog", "Online coding", true, "gym-creation-secret");

    // When
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    // Then
    Gym gym = gymRepository.findByName("Zeus Prog").orElseThrow();
    Membership membership =
        membershipRepository.findByUserIdAndGymId(testUser.getId(), gym.getId()).orElseThrow();
    assertThat(membership.getGymRole()).isEqualTo(GymRole.PROGRAMMER);
  }

  @Test
  @DisplayName("Create Gym: Should return 400 Bad Request for invalid input")
  void testCreateGym_ValidationFailure() throws Exception {
    // Given: Invalid request (empty name)
    GymRequest invalidRequest = new GymRequest("", "", false, "gym-creation-secret");

    // When / Then
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Create Gym: Should return 409 Conflict if name exists")
  void testCreateGym_DuplicateName() throws Exception {
    // Given: Request with an existing name
    GymRequest request = new GymRequest("Active Gym", "Desc", false, "gym-creation-secret");

    // When / Then
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Create Gym: Should fail with Forbidden if token is invalid")
  void testCreateGym_InvalidToken() throws Exception {
    // Given
    GymRequest request = new GymRequest("Spam Box", "Spam", true, "INVALID-TOKEN");

    // When / Then
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Join Gym: Should succeed with valid enrollment code")
  void testJoinGym_Success() throws Exception {
    // Given
    JoinGymRequest request = new JoinGymRequest(activeGym.getId(), "CODE123");

    // When
    mockMvc
        .perform(
            post("/antares/gyms/join")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    // Then: Verify using the exact method name from your Repository
    assertThat(membershipRepository.existsByUserIdAndGymId(testUser.getId(), activeGym.getId()))
        .isTrue();
  }

  @Test
  @DisplayName("Join Gym: Should return 403 Forbidden for invalid code")
  void testJoinGym_InvalidCode() throws Exception {
    // Given
    JoinGymRequest request = new JoinGymRequest(activeGym.getId(), "WRONG-CODE");

    // When / Then
    mockMvc
        .perform(
            post("/antares/gyms/join")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Join Gym: Should return 409 Conflict if already a member")
  void testJoinGym_AlreadyMember() throws Exception {
    // Given: User is already a member
    createMembership(testUser, activeGym, GymRole.ATHLETE);
    JoinGymRequest request = new JoinGymRequest(activeGym.getId(), "CODE123");

    // When / Then
    mockMvc
        .perform(
            post("/antares/gyms/join")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("List Gyms: Admin sees all statuses, User sees only ACTIVE")
  void testListGyms_AdminVsUser() throws Exception {
    // Given
    gymRepository.save(
        Gym.builder().name("Pending Gym").status(GymStatus.PENDING_APPROVAL).build());

    // When: Regular User requests list -> Should see only Active Gym
    mockMvc
        .perform(get("/antares/gyms").cookie(authCookies)) // Uses persisted ATHLETE
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Active Gym"));

    // When: Admin requests a list with filter -> Should see Pending Gym
    // Create Admin via Repo (Register API only creates USER)
    createAdmin("admin@test.com", "password1234");
    Cookie[] adminCookies = login("admin@test.com", "password1234");

    mockMvc
        .perform(get("/antares/gyms").param("status", "PENDING_APPROVAL").cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Pending Gym"));
  }

  @Test
  @DisplayName("Update Status: Only Admin allowed")
  void testUpdateStatus_Security() throws Exception {
    // When: Regular User attempts update -> Forbidden
    mockMvc
        .perform(
            put("/antares/gyms/" + activeGym.getId() + "/status")
                .param("status", "REJECTED")
                .cookie(authCookies)
                .with(csrf()))
        .andExpect(status().isForbidden());

    // When: Admin attempts update -> OK
    createAdmin("admin2@test.com", "password1234");
    Cookie[] adminCookies = login("admin2@test.com", "password1234");

    mockMvc
        .perform(
            put("/antares/gyms/" + activeGym.getId() + "/status")
                .param("status", "REJECTED")
                .cookie(adminCookies)
                .with(csrf()))
        .andExpect(status().isOk());

    // Then
    Gym updated = gymRepository.findById(activeGym.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(GymStatus.REJECTED);
  }

  @Test
  @DisplayName("Settings: Owner should have access, regular member forbidden")
  void testSettings_AccessControl() throws Exception {
    // Given: Initial membership as ATHLETE
    createMembership(testUser, activeGym, GymRole.ATHLETE);

    // When: Regular member attempts to get settings -> Forbidden
    mockMvc
        .perform(
            get("/antares/gyms/" + activeGym.getId() + "/settings")
                .cookie(authCookies)
                .with(csrf()))
        .andExpect(status().isForbidden());

    // Given: Promote user to OWNER in the database
    Membership m =
        membershipRepository
            .findByUserIdAndGymId(testUser.getId(), activeGym.getId())
            .orElseThrow();
    m.setGymRole(GymRole.OWNER);
    membershipRepository.save(m);

    // When: Retry as Owner -> OK
    // Important: AntaresSecurityService checks DB permissions using userId/gymId
    mockMvc
        .perform(
            get("/antares/gyms/" + activeGym.getId() + "/settings")
                .cookie(authCookies)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollmentCode").value("CODE123"));
  }

  @Test
  @DisplayName("Update Settings: Should succeed for Owner")
  void testUpdateSettings_Success() throws Exception {
    // Given: User is Owner
    createMembership(testUser, activeGym, GymRole.OWNER);
    GymSettingsRequest request = new GymSettingsRequest("NEW_CODE", true);

    // When
    mockMvc
        .perform(
            put("/antares/gyms/" + activeGym.getId() + "/settings")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    // Then
    Gym updated = gymRepository.findById(activeGym.getId()).orElseThrow();
    assertThat(updated.getEnrollmentCode()).isEqualTo("NEW_CODE");
    assertThat(updated.isAutoSubscription()).isTrue();
  }

  @Test
  @DisplayName("Delete Gym: Owner allowed, Non-Owner forbidden")
  void testDeleteGym_Security() throws Exception {
    // Given: User is an Owner
    createMembership(testUser, activeGym, GymRole.OWNER);

    // When: Owner deletes -> OK
    mockMvc
        .perform(delete("/antares/gyms/" + activeGym.getId()).cookie(authCookies).with(csrf()))
        .andExpect(status().isNoContent());

    // Then
    assertThat(gymRepository.existsById(activeGym.getId())).isFalse();
  }

  @Test
  @DisplayName("Delete Gym: Should return 403 Forbidden for non-owner")
  void testDeleteGym_Forbidden() throws Exception {
    // Given: User is just an Athlete
    createMembership(testUser, activeGym, GymRole.ATHLETE);

    // When / Then
    mockMvc
        .perform(delete("/antares/gyms/" + activeGym.getId()).with(csrf()).cookie(authCookies))
        .andExpect(status().isForbidden());
  }

  // Helper
  private void createMembership(User user, Gym gym, GymRole role) {
    Membership m =
        Membership.builder()
            .user(user)
            .gym(gym)
            .gymRole(role)
            .status(MembershipStatus.ACTIVE)
            .build();
    membershipRepository.save(m);
  }
}
 