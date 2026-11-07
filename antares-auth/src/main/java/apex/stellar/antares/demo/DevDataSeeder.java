package apex.stellar.antares.demo;

import apex.stellar.antares.model.Division;
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
import java.util.Map;
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
 * Seeds the demo gyms and accounts (roadmap D68). The accounts are <b>demo accounts</b>: flagged
 * {@code demo}, a known password ({@code application.demo.password}), addresses in the reserved {@code .test}
 * domain (no mail can leave), and <b>never an administrator</b> (enforced by {@code User} and a
 * database CHECK). They are meant to stay in production one day; the {@code dev} profile and this
 * class are <b>TEMPORARY</b> until that design is done (protection of the accounts, reset of their
 * data), together with {@code SPRING_PROFILES_ACTIVE: dev} in {@code docker-compose.yml}.
 *
 * <p>Active only with the {@code dev} profile and idempotent per entity: missing gyms (by name),
 * accounts (by email) and memberships (by user and gym) are created, existing ones are left untouched
 * so manual changes made in the application survive restarts. Aldebaran's dev seeder relies on
 * these exact gym names and emails.
 *
 * <ul>
 *   <li>Spartacus CrossFit (physical, ACTIVE, code SPARTA): owner, coach (WOD_WRITE, SCORE_VERIFY,
 *       MANAGE_MEMBERSHIPS), neocoach (no permission), scorer (SCORE_VERIFY), hybrid (WOD_WRITE),
 *       athlete, multi, banned (BANNED), pending (PENDING request), former (INACTIVE, left).
 *   <li>Zeus Programming (virtual, ACTIVE, code OLYMPUS): programmer, active, pending (PENDING),
 *       multi, hybrid (athletes).
 *   <li>Pending Box (physical, PENDING_APPROVAL, code WAIT): wait.pending@demo.test (owner), athlete.pending@demo.test.
 *   <li>free@demo.test: no membership, name shown on public leaderboards.
 * </ul>
 *
 * <p>Divisions (only set while empty): WOMEN for coach, athlete, hybrid (Sparta) and active (Zeus),
 * MEN for owner, scorer, multi, programmer and free; the others keep no division.
 */
@Slf4j
@Component
@Profile("demo")
@Order(2)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final GymRepository gymRepository;
  private final MembershipRepository membershipRepository;
  private final PasswordEncoder passwordEncoder;

  private static final Map<String, Division> DEV_DIVISIONS =
      Map.of(
          "coach.sparta@demo.test", Division.WOMEN,
          "athlete.sparta@demo.test", Division.WOMEN,
          "hybrid.sparta@demo.test", Division.WOMEN,
          "active.zeus@demo.test", Division.WOMEN,
          "owner.sparta@demo.test", Division.MEN,
          "scorer.sparta@demo.test", Division.MEN,
          "multi@demo.test", Division.MEN,
          "programmer.zeus@demo.test", Division.MEN,
          "free@demo.test", Division.MEN);

  private static final String METRIC = "METRIC";

  /**
   * Password of every demo account ({@code application.demo.password}): known and public on purpose
   * (the accounts stay in production, roadmap D68), distinct from the admin's. Empty = no demo
   * accounts are seeded.
   */
  @Value("${application.demo.password:}")
  private String password;

  /**
   * Executes the seeding logic.
   *
   * @param args Raw command line arguments (unused).
   */
  @Override
  @Transactional
  public void run(String @NonNull ... args) {
    if (password == null || password.isBlank()) {
      log.warn("DevDataSeeder: application.demo.password is empty, demo accounts not seeded.");
      return;
    }
    log.warn("DevDataSeeder: ensuring the demo accounts (dev profile).");

    // 1. Spartacus CrossFit (physical)
    Gym sparta = createGym("Spartacus CrossFit", "SPARTA", true, false, GymStatus.ACTIVE);
    User owner = createUser("owner.sparta@demo.test");
    addMembership(owner, sparta, GymRole.OWNER, MembershipStatus.ACTIVE);
    addMembership(
        createUser("coach.sparta@demo.test"),
        sparta,
        GymRole.COACH,
        MembershipStatus.ACTIVE,
        Permission.WOD_WRITE,
        Permission.SCORE_VERIFY,
        Permission.MANAGE_MEMBERSHIPS);
    addMembership(
        createUser("neocoach.sparta@demo.test"), sparta, GymRole.COACH, MembershipStatus.ACTIVE);
    addMembership(
        createUser("scorer.sparta@demo.test"),
        sparta,
        GymRole.COACH,
        MembershipStatus.ACTIVE,
        Permission.SCORE_VERIFY);
    addMembership(
        createUser("athlete.sparta@demo.test"), sparta, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    addMembership(
        createUser("banned.sparta@demo.test"), sparta, GymRole.ATHLETE, MembershipStatus.BANNED);
    addMembership(
        createUser("pending.sparta@demo.test"), sparta, GymRole.ATHLETE, MembershipStatus.PENDING);
    addMembership(
        createUser("former.sparta@demo.test"), sparta, GymRole.ATHLETE, MembershipStatus.INACTIVE);

    // 2. Zeus Programming (virtual)
    Gym zeus = createGym("Zeus Programming", "OLYMPUS", false, true, GymStatus.ACTIVE);
    addMembership(
        createUser("programmer.zeus@demo.test"), zeus, GymRole.PROGRAMMER, MembershipStatus.ACTIVE);
    addMembership(createUser("active.zeus@demo.test"), zeus, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    addMembership(createUser("pending.zeus@demo.test"), zeus, GymRole.ATHLETE, MembershipStatus.PENDING);

    // 3. Members of both gyms
    User multi = createUser("multi@demo.test");
    addMembership(multi, sparta, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    addMembership(multi, zeus, GymRole.ATHLETE, MembershipStatus.ACTIVE);
    User hybrid = createUser("hybrid.sparta@demo.test");
    addMembership(hybrid, sparta, GymRole.COACH, MembershipStatus.ACTIVE, Permission.WOD_WRITE);
    addMembership(hybrid, zeus, GymRole.ATHLETE, MembershipStatus.ACTIVE);

    // 4. Pending Box (waiting for approval)
    Gym pendingBox = createGym("Pending Box", "WAIT", true, false, GymStatus.PENDING_APPROVAL);
    addMembership(createUser("wait.pending@demo.test"), pendingBox, GymRole.OWNER, MembershipStatus.ACTIVE);
    addMembership(
        createUser("athlete.pending@demo.test"), pendingBox, GymRole.ATHLETE, MembershipStatus.ACTIVE);

    // 5. Independent user, visible by name on public leaderboards
    createUser("free@demo.test", true);

    // 6. Divisions for the division leaderboards, without overriding a choice made in the app
    DEV_DIVISIONS.forEach(this::assignDivisionIfEmpty);

    log.info("DevDataSeeder: demo accounts ready.");
  }

  /** Gives the account a division when it has none yet. */
  private void assignDivisionIfEmpty(String email, Division division) {
    userRepository
        .findByEmail(email)
        .filter(user -> user.getDivision() == null)
        .ifPresent(
            user -> {
              user.setDivision(division);
              userRepository.save(user);
            });
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
        .map(this::adopt)
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
                      .emailVerified(true)
                      .demo(true)
                      .unitSystem(METRIC)
                      .publicLeaderboardName(publicLeaderboardName)
                      .build());
            });
  }

  /**
   * Reuses an existing seeded account. A non-demo account under a demo address (registered by hand
   * before the seeder, or created by an older seed) is never given memberships: it would own a demo
   * gym without a demo flag or password.
   */
  private User adopt(User existing) {
    if (!existing.isDemo()) {
      throw new IllegalStateException(
          "DevDataSeeder: " + existing.getEmail() + " exists but is not a demo account");
    }
    if (existing.getUnitSystem() == null) {
      // Loads in kilograms whatever the language, so demos and journeys read the same numbers
      existing.setUnitSystem(METRIC);
      return userRepository.save(existing);
    }
    return existing;
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
