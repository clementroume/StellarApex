package apex.stellar.antares.model;

import static org.junit.jupiter.api.Assertions.*;

import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.Membership.MembershipStatus;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the authorization rules held by {@link Membership}. */
class MembershipTest {

  private Membership membership(
      GymRole role, MembershipStatus status, GymStatus gymStatus, Set<Permission> perms) {
    return Membership.builder()
        .gym(Gym.builder().id(1L).status(gymStatus).build())
        .gymRole(role)
        .status(status)
        .permissions(perms)
        .build();
  }

  // --- Effective permissions ---

  @Test
  @DisplayName("effectivePermissions: Owner gets every permission even with none stored")
  void effectivePermissions_Owner_ShouldGetAll() {
    Membership m = membership(GymRole.OWNER, MembershipStatus.ACTIVE, GymStatus.ACTIVE, Set.of());

    assertEquals(EnumSet.allOf(Permission.class), m.effectivePermissions());
  }

  @Test
  @DisplayName("effectivePermissions: Programmer gets every permission")
  void effectivePermissions_Programmer_ShouldGetAll() {
    Membership m =
        membership(
            GymRole.PROGRAMMER,
            MembershipStatus.ACTIVE,
            GymStatus.ACTIVE,
            Set.of(Permission.WOD_WRITE));

    assertEquals(EnumSet.allOf(Permission.class), m.effectivePermissions());
  }

  @Test
  @DisplayName("effectivePermissions: Coach gets exactly the assigned permissions")
  void effectivePermissions_Coach_ShouldGetAssigned() {
    Membership m =
        membership(
            GymRole.COACH,
            MembershipStatus.ACTIVE,
            GymStatus.ACTIVE,
            Set.of(Permission.WOD_WRITE, Permission.SCORE_VERIFY));

    assertEquals(
        EnumSet.of(Permission.WOD_WRITE, Permission.SCORE_VERIFY), m.effectivePermissions());
  }

  @Test
  @DisplayName("effectivePermissions: Coach without assignment gets none")
  void effectivePermissions_CoachWithoutAssignment_ShouldGetNone() {
    Membership m = membership(GymRole.COACH, MembershipStatus.ACTIVE, GymStatus.ACTIVE, Set.of());

    assertTrue(m.effectivePermissions().isEmpty());
  }

  @Test
  @DisplayName("effectivePermissions: Athlete holding a permission gets none")
  void effectivePermissions_AthleteHoldingPermission_ShouldGetNone() {
    Membership m =
        membership(
            GymRole.ATHLETE,
            MembershipStatus.ACTIVE,
            GymStatus.ACTIVE,
            Set.of(Permission.WOD_WRITE, Permission.MANAGE_SETTINGS));

    assertTrue(m.effectivePermissions().isEmpty());
  }

  @Test
  @DisplayName("effectivePermissions: Missing role or permissions gets none")
  void effectivePermissions_Nulls_ShouldGetNone() {
    assertTrue(Membership.effectivePermissions(null, Set.of(Permission.WOD_WRITE)).isEmpty());
    assertTrue(Membership.effectivePermissions(GymRole.COACH, null).isEmpty());
  }

  // --- Usable gym context ---

  @Test
  @DisplayName("isUsable: Active membership in active gym is usable")
  void isUsable_ActiveMembershipActiveGym_ShouldBeTrue() {
    assertTrue(
        membership(GymRole.ATHLETE, MembershipStatus.ACTIVE, GymStatus.ACTIVE, Set.of())
            .isUsable());
  }

  @Test
  @DisplayName("isUsable: Pending membership is not usable")
  void isUsable_PendingMembership_ShouldBeFalse() {
    assertFalse(
        membership(GymRole.OWNER, MembershipStatus.PENDING, GymStatus.ACTIVE, Set.of())
            .isUsable());
  }

  @Test
  @DisplayName("isUsable: Pending gym is usable by Owner/Programmer only")
  void isUsable_PendingGym_ShouldBeStaffOnly() {
    assertTrue(
        membership(GymRole.OWNER, MembershipStatus.ACTIVE, GymStatus.PENDING_APPROVAL, Set.of())
            .isUsable());
    assertTrue(
        membership(
                GymRole.PROGRAMMER, MembershipStatus.ACTIVE, GymStatus.PENDING_APPROVAL, Set.of())
            .isUsable());
    assertFalse(
        membership(GymRole.COACH, MembershipStatus.ACTIVE, GymStatus.PENDING_APPROVAL, Set.of())
            .isUsable());
    assertFalse(
        membership(GymRole.ATHLETE, MembershipStatus.ACTIVE, GymStatus.PENDING_APPROVAL, Set.of())
            .isUsable());
  }
}
