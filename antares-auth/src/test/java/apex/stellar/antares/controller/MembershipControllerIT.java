package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class MembershipControllerIT extends BaseIntegrationTest {

  @Autowired private GymRepository gymRepository;
  @Autowired private MembershipRepository membershipRepository;

  private User memberUser;
  private Gym gym;

  @BeforeEach
  void setUp() throws Exception {
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    gym =
        gymRepository.save(
            Gym.builder()
                .name("Stellar Box")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("CODE")
                .isAutoSubscription(false)
                .build());

    // Create users with a known password
    User ownerUser = createUser("owner");
    User coachUser = createUser("coach");
    memberUser = createUser("member");

    // Set up Memberships
    createMembership(ownerUser, gym, GymRole.OWNER, MembershipStatus.ACTIVE, Set.of());
    createMembership(
        coachUser,
        gym,
        GymRole.COACH,
        MembershipStatus.ACTIVE,
        Set.of(Permission.MANAGE_MEMBERSHIPS));
    createMembership(memberUser, gym, GymRole.ATHLETE, MembershipStatus.PENDING, Set.of());
  }

  @Test
  @DisplayName("List Memberships: Owner should see list")
  void testGetMemberships_Success() throws Exception {
    Cookie[] cookies = login("owner@test.com", "password1234");

    mockMvc
        .perform(get("/antares/memberships").param("gymId", gym.getId().toString()).cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3)); // Owner, Coach, Member
  }

  @Test
  @DisplayName("List Memberships: Athlete should be forbidden")
  void testGetMemberships_Forbidden() throws Exception {
    // Member user is ATHLETE
    Cookie[] cookies = login("member@test.com", "password1234");

    mockMvc
        .perform(get("/antares/memberships").param("gymId", gym.getId().toString()).cookie(cookies))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Update Membership: Owner promotes Member to Coach")
  void testUpdateMembership_OwnerPromoteToCoach() throws Exception {
    // Given
    Cookie[] cookies = login("owner@test.com", "password1234");
    Membership target =
        membershipRepository.findByUserIdAndGymId(memberUser.getId(), gym.getId()).orElseThrow();

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(
            MembershipStatus.ACTIVE, GymRole.COACH, Set.of(Permission.MANAGE_MEMBERSHIPS));

    // When
    mockMvc
        .perform(
            put("/antares/memberships/" + target.getId())
                .cookie(cookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    // Then
    Membership updated = membershipRepository.findById(target.getId()).orElseThrow();
    assertThat(updated.getGymRole()).isEqualTo(GymRole.COACH);
  }

  @Test
  @DisplayName("Update Membership: Coach validates Member status")
  void testUpdateMembership_CoachValidateUser() throws Exception {
    // Given
    Cookie[] cookies = login("coach@test.com", "password1234");
    Membership target =
        membershipRepository.findByUserIdAndGymId(memberUser.getId(), gym.getId()).orElseThrow();

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.ACTIVE, GymRole.ATHLETE, Set.of());

    // When
    mockMvc
        .perform(
            put("/antares/memberships/" + target.getId())
                .cookie(cookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    // Then
    Membership updated = membershipRepository.findById(target.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
  }

  @Test
  @DisplayName("Update Membership: Coach cannot change Role (Access Denied)")
  void testUpdateMembership_CoachCannotChangeRole() throws Exception {
    // Given: Coach tries to promote Athlete to Coach
    Cookie[] cookies = login("coach@test.com", "password1234");
    Membership target =
        membershipRepository.findByUserIdAndGymId(memberUser.getId(), gym.getId()).orElseThrow();

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(
            MembershipStatus.ACTIVE, GymRole.COACH, Set.of()); // Changed Role

    // When / Then
    mockMvc
        .perform(
            put("/antares/memberships/" + target.getId())
                .cookie(cookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Update Membership: Owner cannot modify Global Admin -> Forbidden")
  void testUpdateMembership_OwnerCannotModifyAdmin() throws Exception {
    // Given: A Global Admin who is also a member of the gym
    User adminUser = createAdmin("globaladmin@test.com", "password1234");
    createMembership(adminUser, gym, GymRole.ATHLETE, MembershipStatus.ACTIVE, Set.of());

    Cookie[] cookies = login("owner@test.com", "password1234");
    Membership target =
        membershipRepository.findByUserIdAndGymId(adminUser.getId(), gym.getId()).orElseThrow();

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.BANNED, GymRole.ATHLETE, Set.of());

    // When / Then
    mockMvc
        .perform(
            put("/antares/memberships/" + target.getId())
                .cookie(cookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Update Membership: Cross-Tenant Forbidden")
  void testUpdateMembership_CrossTenant_Forbidden() throws Exception {
    // Given: Create Gym B and Owner B
    Gym gymB =
        gymRepository.save(
            Gym.builder()
                .name("Gym B")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("B")
                .isAutoSubscription(false)
                .build());
    User ownerB = createUser("ownerB");
    createMembership(ownerB, gymB, GymRole.OWNER, MembershipStatus.ACTIVE, Set.of());

    Cookie[] cookies = login("ownerB@test.com", "password1234");

    // When: Owner B tries to update member of Gym A
    Membership targetInGymA =
        membershipRepository.findByUserIdAndGymId(memberUser.getId(), gym.getId()).orElseThrow();

    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.BANNED, GymRole.ATHLETE, Set.of());

    // Then
    mockMvc
        .perform(
            put("/antares/memberships/" + targetInGymA.getId())
                .cookie(cookies)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Delete Membership: Owner can delete member")
  void testDeleteMembership_Success() throws Exception {
    // Given
    Cookie[] cookies = login("owner@test.com", "password1234");
    Membership target =
        membershipRepository.findByUserIdAndGymId(memberUser.getId(), gym.getId()).orElseThrow();

    // When
    mockMvc
        .perform(delete("/antares/memberships/" + target.getId()).cookie(cookies).with(csrf()))
        .andExpect(status().isNoContent());

    // Then
    assertThat(membershipRepository.existsById(target.getId())).isFalse();
  }

  private User createUser(String username) throws Exception {
    register(username + "@test.com", "password1234");
    return userRepository.findByEmail(username + "@test.com").orElseThrow();
  }

  private void createMembership(User u, Gym g, GymRole r, MembershipStatus s, Set<Permission> p) {
    membershipRepository.save(
        Membership.builder().user(u).gym(g).gymRole(r).status(s).permissions(p).build());
  }
}
