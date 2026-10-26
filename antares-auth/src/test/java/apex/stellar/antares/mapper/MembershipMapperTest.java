package apex.stellar.antares.mapper;

import static org.junit.jupiter.api.Assertions.*;

import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MembershipMapper} (generated implementation). */
class MembershipMapperTest {

  private final MembershipMapper mapper = new MembershipMapperImpl();

  private Membership membership(GymRole role, Set<Permission> perms) {
    return Membership.builder()
        .gym(Gym.builder().id(7L).name("Box").status(GymStatus.ACTIVE).build())
        .gymRole(role)
        .status(MembershipStatus.ACTIVE)
        .permissions(perms)
        .build();
  }

  @Test
  @DisplayName("toSummary: Should flatten gym details")
  void toSummary_ShouldFlattenGym() {
    MembershipSummary summary = mapper.toSummary(membership(GymRole.ATHLETE, Set.of()));

    assertEquals(7L, summary.gymId());
    assertEquals("Box", summary.gymName());
    assertEquals(GymStatus.ACTIVE, summary.gymStatus());
    assertEquals(GymRole.ATHLETE, summary.gymRole());
    assertEquals(MembershipStatus.ACTIVE, summary.status());
  }

  @Test
  @DisplayName("toSummary: Owner exposes every permission")
  void toSummary_Owner_ShouldExposeAllPermissions() {
    MembershipSummary summary = mapper.toSummary(membership(GymRole.OWNER, Set.of()));

    assertEquals(List.of(Permission.values()), summary.permissions());
  }

  @Test
  @DisplayName("toSummary: Coach exposes assigned permissions")
  void toSummary_Coach_ShouldExposeAssignedPermissions() {
    MembershipSummary summary =
        mapper.toSummary(
            membership(GymRole.COACH, Set.of(Permission.SCORE_VERIFY, Permission.WOD_WRITE)));

    assertEquals(List.of(Permission.WOD_WRITE, Permission.SCORE_VERIFY), summary.permissions());
  }

  @Test
  @DisplayName("toSummary: Athlete holding a permission exposes none")
  void toSummary_Athlete_ShouldExposeNoPermission() {
    MembershipSummary summary =
        mapper.toSummary(membership(GymRole.ATHLETE, Set.of(Permission.WOD_WRITE)));

    assertTrue(summary.permissions().isEmpty());
  }
}
