package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.dto.TransferOwnershipRequest;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Integration tests of the role rules by gym type and of the ownership transfer. */
class GymRoleRulesIT extends BaseIntegrationTest {

  private static final String PASSWORD = "password1234";

  @Autowired private GymRepository gymRepository;
  @Autowired private MembershipRepository membershipRepository;

  private Gym box;
  private Gym programming;
  private Membership owner;
  private Membership coach;
  private Membership boxAthlete;
  private Membership programmer;
  private Membership virtualAthlete;

  @BeforeEach
  void setUp() throws Exception {
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    box = createGym("Physical Box", false);
    programming = createGym("Virtual Track", true);

    owner = createMembership("owner", box, GymRole.OWNER, MembershipStatus.ACTIVE);
    coach = createMembership("coach", box, GymRole.COACH, MembershipStatus.ACTIVE);
    coach.setPermissions(Set.of(Permission.WOD_WRITE));
    coach = membershipRepository.save(coach);
    boxAthlete = createMembership("athlete", box, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    programmer =
        createMembership("programmer", programming, GymRole.PROGRAMMER, MembershipStatus.ACTIVE);
    virtualAthlete =
        createMembership("follower", programming, GymRole.ATHLETE, MembershipStatus.ACTIVE);
  }

  // ==================================================================================
  // 1. ROLE RULES ON UPDATE
  // ==================================================================================

  @Test
  @DisplayName("Physical gym: Owner can promote an athlete to coach")
  void physical_promoteToCoach_shouldSucceed() throws Exception {
    update("owner", boxAthlete, GymRole.COACH).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Physical gym: Assigning OWNER should be rejected (400)")
  void physical_assignOwner_shouldBeRejected() throws Exception {
    update("owner", boxAthlete, GymRole.OWNER)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("The owner or programmer role can only be given through an ownership"
                    + " transfer."));
  }

  @Test
  @DisplayName("Physical gym: PROGRAMMER is not allowed (400)")
  void physical_assignProgrammer_shouldBeRejected() throws Exception {
    update("owner", boxAthlete, GymRole.PROGRAMMER)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("The role PROGRAMMER is not allowed in this type of box or program."));
  }

  @Test
  @DisplayName("Physical gym: Changing the owner's role should be rejected, even for an admin")
  void physical_changeOwnerRole_shouldBeRejected() throws Exception {
    createAdmin("admin@test.com", PASSWORD);
    Cookie[] adminCookies = login("admin@test.com", PASSWORD);

    update(adminCookies, owner, GymRole.COACH).andExpect(status().isBadRequest());
    assertThat(reload(owner).getGymRole()).isEqualTo(GymRole.OWNER);
  }

  @Test
  @DisplayName("Virtual gym: COACH is not allowed (400)")
  void virtual_assignCoach_shouldBeRejected() throws Exception {
    update("programmer", virtualAthlete, GymRole.COACH).andExpect(status().isBadRequest());
    assertThat(reload(virtualAthlete).getGymRole()).isEqualTo(GymRole.ATHLETE);
  }

  @Test
  @DisplayName("Virtual gym: Assigning PROGRAMMER should be rejected (400)")
  void virtual_assignProgrammer_shouldBeRejected() throws Exception {
    update("programmer", virtualAthlete, GymRole.PROGRAMMER).andExpect(status().isBadRequest());
  }

  // ==================================================================================
  // 2. OWNERSHIP TRANSFER
  // ==================================================================================

  @Test
  @DisplayName("Physical gym: Owner transfers to a coach; former owner becomes COACH")
  void physical_transfer_shouldSwapRoles() throws Exception {
    transfer(login("owner@test.com", PASSWORD), box, coach)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(owner.getId()))
        .andExpect(jsonPath("$[0].gymRole").value("COACH"))
        .andExpect(jsonPath("$[0].permissions").isEmpty())
        .andExpect(jsonPath("$[1].id").value(coach.getId()))
        .andExpect(jsonPath("$[1].gymRole").value("OWNER"))
        .andExpect(jsonPath("$[1].permissions").isEmpty());

    assertThat(reload(owner).getGymRole()).isEqualTo(GymRole.COACH);
    assertThat(reload(coach).getGymRole()).isEqualTo(GymRole.OWNER);

    // The former owner can no longer transfer
    transfer(login("owner@test.com", PASSWORD), box, owner).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Virtual gym: Admin transfers to an athlete; former programmer becomes ATHLETE")
  void virtual_transfer_byAdmin_shouldSwapRoles() throws Exception {
    createAdmin("admin@test.com", PASSWORD);

    transfer(login("admin@test.com", PASSWORD), programming, virtualAthlete)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].gymRole").value("ATHLETE"))
        .andExpect(jsonPath("$[1].gymRole").value("PROGRAMMER"));

    assertThat(reload(programmer).getGymRole()).isEqualTo(GymRole.ATHLETE);
    assertThat(reload(virtualAthlete).getGymRole()).isEqualTo(GymRole.PROGRAMMER);
  }

  @Test
  @DisplayName("Transfer: A coach (non holder) should be forbidden (403)")
  void transfer_byCoach_shouldBeForbidden() throws Exception {
    transfer(login("coach@test.com", PASSWORD), box, boxAthlete).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Transfer: Pending member or member of another gym should be rejected (400)")
  void transfer_invalidTarget_shouldBeRejected() throws Exception {
    Membership pending = createMembership("pending", box, GymRole.ATHLETE, MembershipStatus.PENDING);
    Cookie[] ownerCookies = login("owner@test.com", PASSWORD);

    transfer(ownerCookies, box, pending).andExpect(status().isBadRequest());
    transfer(ownerCookies, box, virtualAthlete).andExpect(status().isBadRequest());
    transfer(ownerCookies, box, owner).andExpect(status().isBadRequest());

    assertThat(reload(owner).getGymRole()).isEqualTo(GymRole.OWNER);
  }

  // ==================================================================================
  // 3. DATABASE INDEX (V4)
  // ==================================================================================

  @Test
  @DisplayName("Index: A second OWNER/PROGRAMMER in the same gym should be refused by the DB")
  void index_secondHolder_shouldBeRefused() throws Exception {
    User other = createUser("second");
    Membership second =
        Membership.builder()
            .user(other)
            .gym(box)
            .gymRole(GymRole.PROGRAMMER)
            .status(MembershipStatus.ACTIVE)
            .build();

    assertThatThrownBy(() -> membershipRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- Helpers ---

  private ResultActions update(String requester, Membership target, GymRole role)
      throws Exception {
    return update(login(requester + "@test.com", PASSWORD), target, role);
  }

  private ResultActions update(Cookie[] cookies, Membership target, GymRole role)
      throws Exception {
    MembershipUpdateRequest request =
        new MembershipUpdateRequest(MembershipStatus.ACTIVE, role, Set.of());
    return mockMvc.perform(
        put("/antares/memberships/" + target.getId())
            .cookie(cookies)
            .header("X-XSRF-TOKEN", getXsrfToken(cookies))
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(request)));
  }

  private ResultActions transfer(Cookie[] cookies, Gym gym, Membership target) throws Exception {
    return mockMvc.perform(
        post("/antares/gyms/" + gym.getId() + "/transfer-ownership")
            .cookie(cookies)
            .header("X-XSRF-TOKEN", getXsrfToken(cookies))
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(new TransferOwnershipRequest(target.getId()))));
  }

  private Membership reload(Membership membership) {
    return membershipRepository.findById(membership.getId()).orElseThrow();
  }

  private Gym createGym(String name, boolean isProgramming) {
    return gymRepository.save(
        Gym.builder()
            .name(name)
            .status(GymStatus.ACTIVE)
            .enrollmentCode(name.toUpperCase())
            .isProgramming(isProgramming)
            .build());
  }

  private User createUser(String name) throws Exception {
    register(name + "@test.com", PASSWORD);
    return userRepository.findByEmail(name + "@test.com").orElseThrow();
  }

  private Membership createMembership(
      String name, Gym gym, GymRole role, MembershipStatus status) throws Exception {
    return membershipRepository.save(
        Membership.builder()
            .user(createUser(name))
            .gym(gym)
            .gymRole(role)
            .status(status)
            .build());
  }
}
