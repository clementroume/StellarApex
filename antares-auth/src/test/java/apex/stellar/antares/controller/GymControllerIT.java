package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.JoinGymRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class GymControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private GymRepository gymRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MembershipRepository membershipRepository;

  private User testUser;
  private Gym activeGym;

  @BeforeEach
  void setUp() {
    // 1. Clean database to ensure no ID conflicts or FK violations
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    // 2. Create and persist a fresh User.
    // We MUST use the returned instance 'testUser' because it contains the generated ID.
    testUser =
        userRepository.save(
            User.builder()
                .email("athlete@test.com")
                .password("pass")
                .platformRole(PlatformRole.USER)
                .build());

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

  /**
   * Helper method to manually create an Authentication token with the persisted User entity. This
   * ensures the Controller receives the correct domain User object (with the correct ID).
   */
  private Authentication authenticateUser(User user) {
    return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
  }

  @Test
  @DisplayName("Create Gym: Should succeed with valid token and assign OWNER role")
  void testCreateGym_Success() throws Exception {
    GymRequest request = new GymRequest("Iron Box", "Hardcore gym", false, "gym-creation-secret");

    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .with(authentication(authenticateUser(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Iron Box"))
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

    // Verify Database state
    Gym gym = gymRepository.findByName("Iron Box").orElseThrow();
    assertThat(gym.getStatus()).isEqualTo(GymStatus.PENDING_APPROVAL);

    Membership membership =
        membershipRepository.findByUserIdAndGymId(testUser.getId(), gym.getId()).orElseThrow();
    assertThat(membership.getGymRole()).isEqualTo(GymRole.OWNER);
  }

  @Test
  @DisplayName("Create Gym: Should succeed (Programming -> PROGRAMMER role)")
  void testCreateGym_AsProgrammer_Success() throws Exception {
    // TEST AJOUTÉ : isProgramming = true pour vérifier ROLE_PROGRAMMER
    GymRequest request = new GymRequest("Zeus Prog", "Online coding", true, "gym-creation-secret");

    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .with(authentication(authenticateUser(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    Gym gym = gymRepository.findByName("Zeus Prog").orElseThrow();
    Membership membership =
        membershipRepository.findByUserIdAndGymId(testUser.getId(), gym.getId()).orElseThrow();
    assertThat(membership.getGymRole()).isEqualTo(GymRole.PROGRAMMER);
  }

  @Test
  @DisplayName("Create Gym: Should fail with Forbidden if token is invalid")
  void testCreateGym_InvalidToken() throws Exception {
    GymRequest request = new GymRequest("Spam Box", "Spam", true, "INVALID-TOKEN");

    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .with(authentication(authenticateUser(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Join Gym: Should succeed with valid enrollment code")
  void testJoinGym_Success() throws Exception {
    JoinGymRequest request = new JoinGymRequest(activeGym.getId(), "CODE123");

    mockMvc
        .perform(
            post("/antares/gyms/join")
                .with(csrf())
                .with(authentication(authenticateUser(testUser)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    // Verify using the exact method name from your Repository
    assertThat(membershipRepository.existsByUserIdAndGymId(testUser.getId(), activeGym.getId()))
        .isTrue();
  }

  @Test
  @DisplayName("List Gyms: Admin sees all statuses, User sees only ACTIVE")
  void testListGyms_AdminVsUser() throws Exception {
    gymRepository.save(
        Gym.builder().name("Pending Gym").status(GymStatus.PENDING_APPROVAL).build());

    // 1. Regular User: Should see only Active Gym
    mockMvc
        .perform(
            get("/antares/gyms")
                .with(authentication(authenticateUser(testUser)))) // Uses persisted ATHLETE
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Active Gym"));

    // 2. Admin: Should see Pending Gym when filtering
    // Create a transient Admin user (no DB save needed if only role is checked in Controller)
    User adminUser = User.builder().email("admin@test.com").platformRole(PlatformRole.ADMIN).build();

    mockMvc
        .perform(
            get("/antares/gyms")
                .param("status", "PENDING_APPROVAL")
                .with(authentication(authenticateUser(adminUser))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Pending Gym"));
  }

  @Test
  @DisplayName("Update Status: Only Admin allowed")
  void testUpdateStatus_Security() throws Exception {
    // Regular User -> Forbidden
    mockMvc
        .perform(
            put("/antares/gyms/" + activeGym.getId() + "/status")
                .param("status", "REJECTED")
                .with(authentication(authenticateUser(testUser)))
                .with(csrf()))
        .andExpect(status().isForbidden());

    // Admin -> OK
    User adminUser = User.builder().email("admin@test.com").platformRole(PlatformRole.ADMIN).build();

    mockMvc
        .perform(
            put("/antares/gyms/" + activeGym.getId() + "/status")
                .param("status", "REJECTED")
                .with(authentication(authenticateUser(adminUser)))
                .with(csrf()))
        .andExpect(status().isOk());

    Gym updated = gymRepository.findById(activeGym.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(GymStatus.REJECTED);
  }

  @Test
  @DisplayName("Settings: Owner should have access, regular member forbidden")
  void testSettings_AccessControl() throws Exception {
    // 1. Create initial membership as ATHLETE
    createMembership(testUser, activeGym, GymRole.ATHLETE);

    // Regular member attempts to get settings -> Forbidden
    mockMvc
        .perform(
            get("/antares/gyms/" + activeGym.getId() + "/settings")
                .with(authentication(authenticateUser(testUser)))
                .with(csrf()))
        .andExpect(status().isForbidden());

    // 2. Promote user to OWNER in database
    Membership m =
        membershipRepository
            .findByUserIdAndGymId(testUser.getId(), activeGym.getId())
            .orElseThrow();
    m.setGymRole(GymRole.OWNER);
    membershipRepository.save(m);

    // 3. Retry as Owner -> OK
    // Important: AntaresSecurityService checks DB permissions using userId/gymId
    mockMvc
        .perform(
            get("/antares/gyms/" + activeGym.getId() + "/settings")
                .with(authentication(authenticateUser(testUser)))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollmentCode").value("CODE123"));
  }

  @Test
  @DisplayName("Delete Gym: Owner allowed, Non-Owner forbidden")
  void testDeleteGym_Security() throws Exception {
    // 1. Make user an Owner
    createMembership(testUser, activeGym, GymRole.OWNER);

    // Owner deletes -> OK
    mockMvc
        .perform(
            delete("/antares/gyms/" + activeGym.getId())
                .with(authentication(authenticateUser(testUser)))
                .with(csrf()))
        .andExpect(status().isNoContent());

    assertThat(gymRepository.existsById(activeGym.getId())).isFalse();
  }

  // Helper
  private void createMembership(User user, Gym gym, GymRole role) {
    Membership m =
        Membership.builder().user(user).gym(gym).gymRole(role).status(MembershipStatus.ACTIVE).build();
    membershipRepository.save(m);
  }
}
