package apex.stellar.antares.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests of {@link E2eFixtureSeeder}, backed by in-memory repositories. */
@ExtendWith(MockitoExtension.class)
class E2eFixtureSeederTest {

  @Mock private UserRepository userRepository;
  @Mock private GymRepository gymRepository;
  @Mock private MembershipRepository membershipRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private E2eFixtureSeeder seeder;

  private final List<User> users = new ArrayList<>();
  private final List<Gym> gyms = new ArrayList<>();
  private final List<Membership> memberships = new ArrayList<>();
  private final AtomicLong ids = new AtomicLong();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(seeder, "password", "e2e-pw");
    lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded");
    lenient()
        .when(userRepository.findByEmail(anyString()))
        .thenAnswer(
            inv -> users.stream().filter(u -> u.getEmail().equals(inv.getArgument(0))).findFirst());
    lenient()
        .when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              ReflectionTestUtils.setField(u, "id", ids.incrementAndGet());
              users.add(u);
              return u;
            });
    lenient()
        .when(gymRepository.findByName(anyString()))
        .thenAnswer(
            inv -> gyms.stream().filter(g -> g.getName().equals(inv.getArgument(0))).findFirst());
    lenient()
        .when(gymRepository.save(any(Gym.class)))
        .thenAnswer(
            inv -> {
              Gym g = inv.getArgument(0);
              ReflectionTestUtils.setField(g, "id", ids.incrementAndGet());
              gyms.add(g);
              return g;
            });
    lenient()
        .when(membershipRepository.findByUserIdAndGymId(anyLong(), anyLong()))
        .thenAnswer(
            inv ->
                memberships.stream()
                    .filter(m -> m.getUser().getId().equals(inv.getArgument(0)))
                    .findFirst());
    lenient()
        .when(membershipRepository.save(any(Membership.class)))
        .thenAnswer(
            inv -> {
              memberships.add(inv.getArgument(0));
              return inv.getArgument(0);
            });
  }

  @Test
  @DisplayName("run: seeds a box without auto-subscription with an owner, a judge and an athlete")
  void run_SeedsTheBox() {
    seeder.run();

    assertThat(gyms).singleElement().satisfies(g -> {
      assertThat(g.getName()).isEqualTo(E2eFixtureSeeder.BOX_NAME);
      assertThat(g.isAutoSubscription()).isFalse();
      assertThat(g.getEnrollmentCode()).isEqualTo(E2eFixtureSeeder.BOX_CODE);
    });
    assertThat(users).hasSize(3);
    assertThat(users).allMatch(u -> !u.isDemo() && u.getPlatformRole() == PlatformRole.USER);
    assertThat(users).allMatch(User::isEmailVerified);
    assertThat(memberships).extracting(Membership::getGymRole)
        .containsExactlyInAnyOrder(GymRole.OWNER, GymRole.COACH, GymRole.ATHLETE);
    Membership judge =
        memberships.stream().filter(m -> m.getGymRole() == GymRole.COACH).findFirst().orElseThrow();
    assertThat(judge.getPermissions())
        .containsExactlyInAnyOrder(Permission.WOD_WRITE, Permission.SCORE_VERIFY);
  }

  @Test
  @DisplayName("run: is a no-op when run twice")
  void run_Twice_CreatesNothingMore() {
    seeder.run();
    seeder.run();

    assertThat(gyms).hasSize(1);
    assertThat(users).hasSize(3);
    assertThat(memberships).hasSize(3);
  }

  @Test
  @DisplayName("run: nothing is seeded without a configured password")
  void run_NoPassword_SeedsNothing() {
    ReflectionTestUtils.setField(seeder, "password", "");

    seeder.run();

    assertThat(users).isEmpty();
    assertThat(gyms).isEmpty();
  }
}
