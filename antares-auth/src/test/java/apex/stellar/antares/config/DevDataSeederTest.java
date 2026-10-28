package apex.stellar.antares.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

/**
 * Unit tests for {@link DevDataSeeder}, backed by in-memory repositories.
 *
 * <p>Verifies the per-entity idempotency: a database seeded by an older version only receives the
 * missing gyms, accounts and memberships.
 */
@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

  private static final int ACCOUNTS = 16;
  private static final int MEMBERSHIPS = 17;

  @Mock private UserRepository userRepository;
  @Mock private GymRepository gymRepository;
  @Mock private MembershipRepository membershipRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private DevDataSeeder seeder;

  private final List<User> users = new ArrayList<>();
  private final List<Gym> gyms = new ArrayList<>();
  private final List<Membership> memberships = new ArrayList<>();
  private final AtomicLong ids = new AtomicLong();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(seeder, "password", "secret");
    lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded");

    lenient()
        .when(userRepository.findByEmail(anyString()))
        .thenAnswer(
            inv -> users.stream().filter(u -> u.getEmail().equals(inv.getArgument(0))).findFirst());
    lenient()
        .when(userRepository.save(any(User.class)))
        .thenAnswer(inv -> store(inv.getArgument(0), users));

    lenient()
        .when(gymRepository.findByName(anyString()))
        .thenAnswer(
            inv -> gyms.stream().filter(g -> g.getName().equals(inv.getArgument(0))).findFirst());
    lenient()
        .when(gymRepository.save(any(Gym.class)))
        .thenAnswer(inv -> store(inv.getArgument(0), gyms));

    lenient()
        .when(membershipRepository.findByUserIdAndGymId(anyLong(), anyLong()))
        .thenAnswer(
            inv ->
                memberships.stream()
                    .filter(
                        m ->
                            Objects.equals(m.getUser().getId(), inv.getArgument(0))
                                && Objects.equals(m.getGym().getId(), inv.getArgument(1)))
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
  @DisplayName("run: should seed every gym, account and membership on an empty database")
  void testRun_EmptyDatabase_SeedsEverything() {
    seeder.run();

    assertThat(gyms)
        .extracting(Gym::getName)
        .containsExactlyInAnyOrder("Spartacus CrossFit", "Zeus Programming", "Pending Box");
    assertThat(users).hasSize(ACCOUNTS);
    assertThat(memberships).hasSize(MEMBERSHIPS);
    assertThat(membership("banned@sparta.com", "Spartacus CrossFit").getStatus())
        .isEqualTo(MembershipStatus.BANNED);
  }

  @Test
  @DisplayName("run: should be a no-op when run twice")
  void testRun_Twice_CreatesNothingMore() {
    seeder.run();
    seeder.run();

    assertThat(gyms).hasSize(3);
    assertThat(users).hasSize(ACCOUNTS);
    assertThat(memberships).hasSize(MEMBERSHIPS);
  }

  @Test
  @DisplayName("run: should only add what is missing on a database seeded by an older version")
  void testRun_OlderSeed_AddsMissingAccountsOnly() {
    Gym sparta = gym("Spartacus CrossFit", false);
    Gym zeus = gym("Zeus Programming", true);
    // A manual change made in the application must survive the seeder
    User owner = user("owner@sparta.com");
    owner.setFirstName("Renamed");
    member(owner, sparta, GymRole.OWNER);
    member(user("programmer@zeus.com"), zeus, GymRole.PROGRAMMER);

    seeder.run();

    assertThat(gyms).hasSize(3);
    assertThat(gyms.stream().filter(g -> g.getName().equals("Spartacus CrossFit"))).hasSize(1);
    assertThat(users).hasSize(ACCOUNTS);
    assertThat(owner.getFirstName()).isEqualTo("Renamed");
    assertThat(memberships).hasSize(MEMBERSHIPS);
    assertThat(membership("multi@athlete.com", "Zeus Programming").getGymRole())
        .isEqualTo(GymRole.ATHLETE);
    assertThat(membership("hybrid@sparta.com", "Spartacus CrossFit").getGymRole())
        .isEqualTo(GymRole.COACH);
  }

  // --- HELPERS ---

  private <T> T store(T entity, List<T> target) {
    ReflectionTestUtils.setField(entity, "id", ids.incrementAndGet());
    target.add(entity);
    return entity;
  }

  private Gym gym(String name, boolean isProgramming) {
    return store(
        Gym.builder().name(name).isProgramming(isProgramming).status(GymStatus.ACTIVE).build(),
        gyms);
  }

  private User user(String email) {
    return store(User.builder().email(email).firstName("x").lastName("User").build(), users);
  }

  private void member(User user, Gym gym, GymRole role) {
    memberships.add(
        Membership.builder()
            .user(user)
            .gym(gym)
            .gymRole(role)
            .status(MembershipStatus.ACTIVE)
            .build());
  }

  private Membership membership(String email, String gymName) {
    Optional<Membership> found =
        memberships.stream()
            .filter(
                m ->
                    m.getUser().getEmail().equals(email) && m.getGym().getName().equals(gymName))
            .findFirst();
    assertThat(found).as("membership %s @ %s", email, gymName).isPresent();
    return found.get();
  }
}
