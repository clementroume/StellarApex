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
import apex.stellar.antares.dto.GymUpdateRequest;
import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
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
    GymRequest request = new GymRequest("Iron Box", "Hardcore gym", false);

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
    GymRequest request = new GymRequest("Zeus Prog", "Online coding", true);

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
    GymRequest invalidRequest = new GymRequest("", "", false);

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
    GymRequest request = new GymRequest("Active Gym", "Desc", false);

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
  @DisplayName("Create Gym: open to any user, one request waiting for approval at a time")
  void testCreateGym_OnePendingRequest() throws Exception {
    // Given: a first request waits for approval
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new GymRequest("First Box", null, false))))
        .andExpect(status().isCreated());

    // When / Then: a second one is refused
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new GymRequest("Second Box", null, true))))
        .andExpect(status().isConflict());

    // When the first one is approved, a new request is possible
    Gym first = gymRepository.findByName("First Box").orElseThrow();
    first.setStatus(GymStatus.ACTIVE);
    gymRepository.save(first);
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new GymRequest("Second Box", null, true))))
        .andExpect(status().isCreated());
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
  @DisplayName("Join Gym: Should return 400 Bad Request for invalid code")
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
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Leave and join again: INACTIVE keeps the membership, joining reactivates it")
  void testLeaveAndRejoin() throws Exception {
    // Given: an ACTIVE coach
    createMembership(testUser, activeGym, GymRole.COACH);
    Long membershipId =
        membershipRepository
            .findByUserIdAndGymId(testUser.getId(), activeGym.getId())
            .orElseThrow()
            .getId();

    // When: leaving
    mockMvc
        .perform(
            delete("/antares/gyms/" + activeGym.getId() + "/membership/me")
                .with(csrf())
                .cookie(authCookies))
        .andExpect(status().isNoContent());

    // Then: same membership, INACTIVE, no access any more
    Membership left = membershipRepository.findById(membershipId).orElseThrow();
    assertThat(left.getStatus()).isEqualTo(MembershipStatus.INACTIVE);

    // When: joining again, the same row comes back as a pending athlete
    mockMvc
        .perform(
            post("/antares/gyms/join")
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new JoinGymRequest(activeGym.getId(), "code123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gymName").value("Active Gym"))
        .andExpect(jsonPath("$.status").value("PENDING"));
    Membership back = membershipRepository.findById(membershipId).orElseThrow();
    assertThat(back.getGymRole()).isEqualTo(GymRole.ATHLETE);
  }

  @Test
  @DisplayName("Leave: the owner must transfer the gym first (409)")
  void testLeave_Holder() throws Exception {
    createMembership(testUser, activeGym, GymRole.OWNER);

    mockMvc
        .perform(
            delete("/antares/gyms/" + activeGym.getId() + "/membership/me")
                .with(csrf())
                .cookie(authCookies))
        .andExpect(status().isConflict());
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
  @DisplayName("Search Gyms: ACTIVE gyms only, public view; admins search every status")
  void testSearchGyms_UserVsAdmin() throws Exception {
    // Given
    gymRepository.save(
        Gym.builder().name("Pending Gym").status(GymStatus.PENDING_APPROVAL).build());
    gymRepository.save(
        Gym.builder().name("Active Program").isProgramming(true).status(GymStatus.ACTIVE).build());

    // When: a user searches -> ACTIVE gyms, never the code
    mockMvc
        .perform(get("/antares/gyms").param("search", "active").cookie(authCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Active Gym"))
        .andExpect(jsonPath("$.content[0].enrollmentCode").doesNotExist())
        .andExpect(jsonPath("$.content[0].isAutoSubscription").doesNotExist());
    mockMvc
        .perform(get("/antares/gyms").param("type", "PROGRAM").cookie(authCookies))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Active Program"));
    mockMvc
        .perform(get("/antares/admin/gyms").cookie(authCookies))
        .andExpect(status().isForbidden());

    // When: an admin looks at the approval queue
    createAdmin("admin@test.com", "password1234");
    Cookie[] adminCookies = login("admin@test.com", "password1234");

    mockMvc
        .perform(
            get("/antares/admin/gyms").param("status", "PENDING_APPROVAL").cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Pending Gym"))
        .andExpect(jsonPath("$.content[0].memberCount").value(0));
  }

  @Test
  @DisplayName("Get Gym: public view of an ACTIVE gym only")
  void testGetGym() throws Exception {
    Gym pending =
        gymRepository.save(
            Gym.builder().name("Pending Gym").status(GymStatus.PENDING_APPROVAL).build());

    mockMvc
        .perform(get("/antares/gyms/" + activeGym.getId()).cookie(authCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Active Gym"))
        .andExpect(jsonPath("$.enrollmentCode").doesNotExist());
    mockMvc
        .perform(get("/antares/gyms/" + pending.getId()).cookie(authCookies))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Update Gym and regenerate code: MANAGE_SETTINGS only")
  void testUpdateGymAndCode() throws Exception {
    createMembership(testUser, activeGym, GymRole.ATHLETE);
    String body = jsonMapper.writeValueAsString(new GymUpdateRequest("Renamed Gym", "New desc"));

    mockMvc
        .perform(
            put("/antares/gyms/" + activeGym.getId())
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    Membership m =
        membershipRepository
            .findByUserIdAndGymId(testUser.getId(), activeGym.getId())
            .orElseThrow();
    m.setGymRole(GymRole.OWNER);
    membershipRepository.save(m);

    mockMvc
        .perform(
            put("/antares/gyms/" + activeGym.getId())
                .with(csrf())
                .cookie(authCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed Gym"))
        .andExpect(jsonPath("$.description").value("New desc"));

    mockMvc
        .perform(
            post("/antares/gyms/" + activeGym.getId() + "/settings/enrollment-code")
                .with(csrf())
                .cookie(authCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollmentCode").value(org.hamcrest.Matchers.not("CODE123")));
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
  @DisplayName("Delete Gym: a coach holding MANAGE_SETTINGS cannot delete the gym")
  void testDeleteGym_CoachWithSettings_Forbidden() throws Exception {
    membershipRepository.save(
        Membership.builder()
            .user(testUser)
            .gym(activeGym)
            .gymRole(GymRole.COACH)
            .status(MembershipStatus.ACTIVE)
            .permissions(new java.util.HashSet<>(java.util.Set.of(Permission.MANAGE_SETTINGS)))
            .build());

    mockMvc
        .perform(delete("/antares/gyms/" + activeGym.getId()).with(csrf()).cookie(authCookies))
        .andExpect(status().isForbidden());
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

  @Test
  @DisplayName("Athletes: SCORE_VERIFY lists usable members by name, athletes are forbidden")
  void testAthletes_AccessControl() throws Exception {
    // Given: the caller is an athlete, plus one pending member who must not be listed
    createMembership(testUser, activeGym, GymRole.ATHLETE);
    register("pending@test.com", "password1234");
    User pending = userRepository.findByEmail("pending@test.com").orElseThrow();
    membershipRepository.save(
        Membership.builder()
            .user(pending)
            .gym(activeGym)
            .gymRole(GymRole.ATHLETE)
            .status(MembershipStatus.PENDING)
            .build());

    // When: an athlete asks -> Forbidden
    mockMvc
        .perform(get("/antares/gyms/" + activeGym.getId() + "/athletes").cookie(authCookies))
        .andExpect(status().isForbidden());

    // Given: the caller becomes a coach holding SCORE_VERIFY
    Membership m =
        membershipRepository
            .findByUserIdAndGymId(testUser.getId(), activeGym.getId())
            .orElseThrow();
    m.setGymRole(GymRole.COACH);
    m.getPermissions().add(apex.stellar.antares.model.Permission.SCORE_VERIFY);
    membershipRepository.save(m);

    // Then: only the usable member (the coach) is listed, with a display name
    mockMvc
        .perform(get("/antares/gyms/" + activeGym.getId() + "/athletes").cookie(authCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(testUser.getId()))
        .andExpect(jsonPath("$[0].displayName").value("Test U."));
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
 