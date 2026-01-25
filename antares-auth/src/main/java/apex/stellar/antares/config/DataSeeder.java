package apex.stellar.antares.config;

import apex.stellar.antares.model.*;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.repository.GymRepository;
import apex.stellar.antares.repository.MembershipRepository;
import apex.stellar.antares.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Component responsible for seeding the database with initial demo data upon application startup.
 *
 * <p>This seeder is <b>idempotent</b>: it checks for the existence of a reference Gym ("Spartacus
 * CrossFit") before attempting insertion. It creates a complete ecosystem including:
 *
 * <ul>
 *   <li>A physical gym with an Owner, a Coach, and an Athlete.
 *   <li>A virtual programming gym with a Programmer and Athletes.
 *   <li>A pending gym waiting for validation.
 *   <li>An independent user without memberships.
 * </ul>
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

  // Explicitly define the full set of permissions for Owners/Programmers
  private static final Set<Permission> FULL_ADMIN_PERMISSIONS =
      Set.of(
          Permission.MANAGE_SETTINGS,
          Permission.MANAGE_MEMBERSHIPS,
          Permission.WOD_WRITE,
          Permission.SCORE_VERIFY);

  private final UserRepository userRepository;
  private final GymRepository gymRepository;
  private final MembershipRepository membershipRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${application.admin.default-password}")
  private String adminPassword;

  /**
   * Executes the seeding logic.
   *
   * @param args Raw command line arguments (unused).
   */
  @Override
  @Transactional
  public void run(String @NonNull ... args) {
    if (gymRepository.existsByName("Spartacus CrossFit")) {
      log.info("DataSeeder: Data already exists, skipping.");
      return;
    }

    log.info("DataSeeder: Seeding initial data...");

    // 1. Spartacus CrossFit (Physical Gym)
    Gym sparta = createGym("Spartacus CrossFit", "SPARTA", true, false, GymStatus.ACTIVE);

    // Owner gets FULL permissions explicitly
    createUserAndMembership(
        "owner@sparta.com",
        GymRole.OWNER,
        MembershipStatus.ACTIVE,
        sparta,
        FULL_ADMIN_PERMISSIONS);

    // Coach gets WOD management AND Membership management (to test delegation)
    createUserAndMembership(
        "coach@sparta.com",
        GymRole.COACH,
        MembershipStatus.ACTIVE,
        sparta,
        Set.of(Permission.WOD_WRITE, Permission.SCORE_VERIFY, Permission.MANAGE_MEMBERSHIPS));

    createUserAndMembership(
        "athlete@sparta.com", GymRole.ATHLETE, MembershipStatus.ACTIVE, sparta, Set.of());

    // 2. Zeus Programming (Virtual)
    Gym zeus = createGym("Zeus Programming", "OLYMPUS", false, true, GymStatus.ACTIVE);

    createUserAndMembership(
        "prog@zeus.com",
        GymRole.PROGRAMMER,
        MembershipStatus.ACTIVE,
        zeus,
        FULL_ADMIN_PERMISSIONS);

    createUserAndMembership(
        "active@zeus.com", GymRole.ATHLETE, MembershipStatus.ACTIVE, zeus, Set.of());
    createUserAndMembership(
        "pending@zeus.com", GymRole.ATHLETE, MembershipStatus.PENDING, zeus, Set.of());

    // 3. Pending Box (Waiting for approval)
    Gym pendingBox = createGym("Pending Box", "WAIT", true, false, GymStatus.PENDING_APPROVAL);
    createUserAndMembership(
        "wait@box.com",
        GymRole.OWNER,
        MembershipStatus.ACTIVE,
        pendingBox,
        FULL_ADMIN_PERMISSIONS);

    // 4. Independent User
    createUser("free@athlete.com");

    log.info("DataSeeder: Seeding complete.");
  }

  /** Helper to create and save a Gym entity. */
  private Gym createGym(
      String name, String code, boolean autoSub, boolean isProgramming, GymStatus status) {
    return gymRepository.save(
        Gym.builder()
            .name(name)
            .enrollmentCode(code)
            .isAutoSubscription(autoSub)
            .status(status)
            .isProgramming(isProgramming)
            .build());
  }

  /** Helper to create a User and link them to a Gym via a Membership. */
  private void createUserAndMembership(
      String email, GymRole gymRole, MembershipStatus status, Gym gym, Set<Permission> perms) {
    User user = createUser(email);
    membershipRepository.save(
        Membership.builder()
            .user(user)
            .gym(gym)
            .gymRole(gymRole)
            .status(status)
            .permissions(perms)
            .build());
  }

  /** Helper to create and save a User entity with a hashed password. */
  private User createUser(String email) {
    return userRepository.save(
        User.builder()
            .email(email)
            .password(passwordEncoder.encode(adminPassword))
            .firstName(email.split("@")[0])
            .lastName("User")
            .platformRole(PlatformRole.USER)
            .build());
  }
}
