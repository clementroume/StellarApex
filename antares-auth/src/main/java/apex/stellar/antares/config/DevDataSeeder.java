package apex.stellar.antares.config;

import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds test gyms and accounts for local development. <b>TEMPORARY: TO BE REMOVED</b> (like the
 * Aldebaran CSV export), together with {@code SPRING_PROFILES_ACTIVE: dev} in {@code
 * docker-compose.yml}.
 *
 * <p>Active only with the {@code dev} profile and idempotent per entity: missing gyms (by name),
 * accounts (by email) and memberships (by user and gym) are created, existing ones are left untouched
 * so manual changes made in the application survive restarts. New accounts use {@code
 * application.admin.default-password}. Aldebaran's dev seeder
 * relies on these exact gym names and emails.
 *
 * <ul>
 *   <li>Spartacus CrossFit (physical, ACTIVE, code SPARTA): owner, coach (WOD_WRITE, SCORE_VERIFY,
 *       MANAGE_MEMBERSHIPS), neocoach (no permission), scorer (SCORE_VERIFY), hybrid (WOD_WRITE),
 *       athlete, multi, banned (BANNED).
 *   <li>Zeus Programming (virtual, ACTIVE, code OLYMPUS): programmer, active, pending (PENDING),
 *       multi, hybrid (athletes).
 *   <li>Pending Box (physical, PENDING_APPROVAL, code WAIT): wait@box.com (owner), athlete@box.com.
 *   <li>free@athlete.com: no membership, name shown on public leaderboards.
 * </ul>
 */
@Slf4j
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final GymRepository gymRepository;
  private final MembershipRepository membershipRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${application.admin.default-password}")
  private String password;

  /**
   * Executes the seeding logic.
   *
   * @param args Raw command line arguments (unused).
   */
  @Override
  @Transactional
  public void run(String @NonNull ... args) {
    log.warn("DevDataSeeder: ensuring TEMPORARY test accounts (dev profile).");

    // 1. Spartacus CrossFit (physical)
    Gym sparta = createGym("Spartacus CrossFit", "SPARTA", true, false, GymStatus.ACTIVE);
    User owner = createUser("owner@sparta.com");
    addMembership(owner, sparta, GymRole.OWNER, MembershipStatus.ACTIVE);
    addMembership(
        createUser("coach@sparta.com"),
        sparta,
        GymRole.COACH,
        MembershipStatus.ACTIVE,
        Permission.WOD_WRITE,
        Permission.SCORE_VERIFY,
        Permission.MANAGE_MEMBERSHIPS);
    addMembership(
        createUser("neocoach@sparta.com"), sparta, GymRole.COACH, MembershipStatus.ACTIVE);
    addMembership(
        createUser("scorer@sparta.com"),
        sparta,
        GymRole.COACH,
        MembershipStatus.ACTIVE,
        Permission.SCORE_VERIFY);
    addMembership(
        createUser("athlete@sparta.com"), sparta, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    addMembership(
        createUser("banned@sparta.com"), sparta, GymRole.ATHLETE, MembershipStatus.BANNED);

    // 2. Zeus Programming (virtual)
    Gym zeus = createGym("Zeus Programming", "OLYMPUS", false, true, GymStatus.ACTIVE);
    addMembership(
        createUser("programmer@zeus.com"), zeus, GymRole.PROGRAMMER, MembershipStatus.ACTIVE);
    addMembership(createUser("active@zeus.com"), zeus, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    addMembership(createUser("pending@zeus.com"), zeus, GymRole.ATHLETE, MembershipStatus.PENDING);

    // 3. Members of both gyms
    User multi = createUser("multi@athlete.com");
    addMembership(multi, sparta, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    addMembership(multi, zeus, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    User hybrid = createUser("hybrid@sparta.com");
    addMembership(hybrid, sparta, GymRole.COACH, MembershipStatus.ACTIVE, Permission.WOD_WRITE);
    addMembership(hybrid, zeus, GymRole.ATHLETE, MembershipStatus.ACTIVE);

    // 4. Pending Box (waiting for approval)
    Gym pendingBox = createGym("Pending Box", "WAIT", true, false, GymStatus.PENDING_APPROVAL);
    addMembership(createUser("wait@box.com"), pendingBox, GymRole.OWNER, MembershipStatus.ACTIVE);
    addMembership(
        createUser("athlete@box.com"), pendingBox, GymRole.ATHLETE, MembershipStatus.ACTIVE);

    // 5. Independent user, visible by name on public leaderboards
    createUser("free@athlete.com", true);

    log.info("DevDataSeeder: test accounts ready.");
  }

  /** Returns the account with this email, creating a USER named after its local part if missing. */
  private User createUser(String email) {
    return createUser(email, false);
  }

  /**
   * Same as {@link #createUser(String)}, choosing whether a new account shows its name on public
   * leaderboards.
   */
  private User createUser(String email, boolean publicLeaderboardName) {
    return userRepository
        .findByEmail(email)
        .orElseGet(
            () -> {
              log.info("DevDataSeeder: creating account {}", email);
              return userRepository.save(
                  User.builder()
                      .email(email)
                      .password(passwordEncoder.encode(password))
                      .firstName(email.split("@")[0])
                      .lastName("User")
                      .platformRole(PlatformRole.USER)
                      .publicLeaderboardName(publicLeaderboardName)
                      .build());
            });
  }

  /** Returns the gym with this name, creating it if missing. */
  private Gym createGym(
      String name, String code, boolean autoSub, boolean isProgramming, GymStatus status) {
    return gymRepository
        .findByName(name)
        .orElseGet(
            () -> {
              log.info("DevDataSeeder: creating gym {}", name);
              return gymRepository.save(
                  Gym.builder()
                      .name(name)
                      .enrollmentCode(code)
                      .isAutoSubscription(autoSub)
                      .isProgramming(isProgramming)
                      .status(status)
                      .build());
            });
  }

  /** Adds the membership unless the user already belongs to the gym. */
  private void addMembership(
      User user, Gym gym, GymRole role, MembershipStatus status, Permission... permissions) {
    if (membershipRepository.findByUserIdAndGymId(user.getId(), gym.getId()).isPresent()) {
      return;
    }
    membershipRepository.save(
        Membership.builder()
            .user(user)
            .gym(gym)
            .gymRole(role)
            .status(status)
            .permissions(new HashSet<>(Set.of(permissions)))
            .build());
  }
}
