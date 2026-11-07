package apex.stellar.antares.e2e;

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
 * Seeds the fixtures the Playwright journeys need, independently of the demo data: the box "E2E Box"
 * (physical, ACTIVE, code {@value #BOX_CODE}, <b>no auto-subscription</b>, so a join request waits
 * for its Owner) with its Owner, a judge (COACH with WOD_WRITE and SCORE_VERIFY) and an athlete.
 *
 * <p>Active only with the {@code e2e} profile and idempotent per entity (gym by name, account by
 * email, membership by user and gym). The accounts are ordinary users: not demo accounts, never
 * administrators. Their addresses use the reserved {@code .test} domain; the password comes from
 * {@code application.e2e.password} (empty = nothing seeded). To use the journeys elsewhere, activate
 * this profile there; to drop the fixtures, delete this package. The addresses and the box are
 * mirrored in {@code sirius-app/e2e/support/env.ts}.
 */
@Slf4j
@Component
@Profile("e2e")
@Order(3)
@RequiredArgsConstructor
public class E2eFixtureSeeder implements CommandLineRunner {

  /** Name of the fixture box. */
  public static final String BOX_NAME = "E2E Box";

  /** Join code of the fixture box. */
  public static final String BOX_CODE = "E2EBOX";

  private final UserRepository userRepository;
  private final GymRepository gymRepository;
  private final MembershipRepository membershipRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${application.e2e.password:}")
  private String password;

  /**
   * Creates the missing fixtures.
   *
   * @param args Raw command line arguments (unused).
   */
  @Override
  @Transactional
  public void run(String @NonNull ... args) {
    if (password == null || password.isBlank()) {
      log.warn("E2eFixtureSeeder: application.e2e.password is empty, fixtures not seeded.");
      return;
    }
    Gym box =
        gymRepository
            .findByName(BOX_NAME)
            .orElseGet(
                () ->
                    gymRepository.save(
                        Gym.builder()
                            .name(BOX_NAME)
                            .enrollmentCode(BOX_CODE)
                            .isAutoSubscription(false)
                            .isProgramming(false)
                            .status(GymStatus.ACTIVE)
                            .build()));
    addMembership(account("e2e.owner@e2e.test"), box, GymRole.OWNER);
    addMembership(
        account("e2e.judge@e2e.test"),
        box,
        GymRole.COACH,
        Permission.WOD_WRITE,
        Permission.SCORE_VERIFY);
    addMembership(account("e2e.athlete@e2e.test"), box, GymRole.ATHLETE);
    log.info("E2eFixtureSeeder: fixtures ready.");
  }

  private User account(String email) {
    return userRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                userRepository.save(
                    User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(password))
                        .firstName(email.split("@")[0])
                        .lastName("E2E")
                        .platformRole(PlatformRole.USER)
                        .emailVerified(true)
                        // Loads in kilograms whatever the language: the journeys read the same numbers
                        .unitSystem("METRIC")
                        .build()));
  }

  private void addMembership(User user, Gym gym, GymRole role, Permission... permissions) {
    if (membershipRepository.findByUserIdAndGymId(user.getId(), gym.getId()).isPresent()) {
      return;
    }
    membershipRepository.save(
        Membership.builder()
            .user(user)
            .gym(gym)
            .gymRole(role)
            .status(MembershipStatus.ACTIVE)
            .permissions(new HashSet<>(Set.of(permissions)))
            .build());
  }
}
