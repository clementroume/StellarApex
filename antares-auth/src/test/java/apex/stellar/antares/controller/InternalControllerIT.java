package apex.stellar.antares.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InternalControllerIT extends BaseIntegrationTest {

  private static final String SECRET = "test-internal-secret";

  @Autowired private GymRepository gymRepository;
  @Autowired private MembershipRepository membershipRepository;

  private User athlete;
  private Gym gym;
  private Cookie[] athleteCookies;

  @BeforeEach
  void setUp() throws Exception {
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    athleteCookies = registerAndLogin("member@test.com", "password1234");
    athlete = userRepository.findByEmail("member@test.com").orElseThrow();
    gym =
        gymRepository.save(
            Gym.builder()
                .name("Internal Box")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("INT123")
                .isAutoSubscription(false)
                .build());
  }

  private String url() {
    return "/antares/internal/gyms/" + gym.getId() + "/members/" + athlete.getId();
  }

  @Test
  @DisplayName("Member check: true for a usable membership, with the secret")
  void member_withSecret_returnsTrue() throws Exception {
    saveMembership(MembershipStatus.ACTIVE);

    mockMvc
        .perform(get(url()).header("X-Internal-Secret", SECRET))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member").value(true));
  }

  @Test
  @DisplayName("Member check: false for a pending membership or none")
  void member_notUsable_returnsFalse() throws Exception {
    mockMvc
        .perform(get(url()).header("X-Internal-Secret", SECRET))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member").value(false));

    saveMembership(MembershipStatus.PENDING);
    mockMvc
        .perform(get(url()).header("X-Internal-Secret", SECRET))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member").value(false));
  }

  @Test
  @DisplayName("Member check: 404 without the secret or with a wrong one, even when logged in")
  void member_withoutSecret_isHidden() throws Exception {
    saveMembership(MembershipStatus.ACTIVE);

    mockMvc.perform(get(url())).andExpect(status().isNotFound());
    mockMvc
        .perform(get(url()).header("X-Internal-Secret", "wrong"))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(url()).cookie(athleteCookies)).andExpect(status().isNotFound());
  }

  private void saveMembership(MembershipStatus status) {
    membershipRepository.save(
        Membership.builder()
            .user(athlete)
            .gym(gym)
            .gymRole(GymRole.ATHLETE)
            .status(status)
            .build());
  }

  @Test
  @DisplayName("Gym type: physical or programming, 404 for an unknown gym or without the secret")
  void gymType() throws Exception {
    Gym prog =
        gymRepository.save(
            Gym.builder()
                .name("Internal Prog")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("PROG1")
                .isProgramming(true)
                .isAutoSubscription(false)
                .build());

    mockMvc
        .perform(get("/antares/internal/gyms/" + gym.getId()).header("X-Internal-Secret", SECRET))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.programming").value(false));
    mockMvc
        .perform(get("/antares/internal/gyms/" + prog.getId()).header("X-Internal-Secret", SECRET))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.programming").value(true));
    mockMvc
        .perform(get("/antares/internal/gyms/999999").header("X-Internal-Secret", SECRET))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/antares/internal/gyms/" + prog.getId()))
        .andExpect(status().isNotFound());
  }
}
