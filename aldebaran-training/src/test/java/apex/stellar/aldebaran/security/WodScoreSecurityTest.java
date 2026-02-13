package apex.stellar.aldebaran.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WodScoreSecurityTest {

  @Mock private WodScoreRepository scoreRepository;
  @Mock private WodRepository wodRepository;
  @Mock private SecurityService securityService;
  @InjectMocks private WodScoreSecurity scoreSecurity;

  // --- canView Checks ---

  @Test
  @DisplayName("canView: Admin is always allowed")
  void canView_Admin_ReturnsTrue() {
    AldebaranUserPrincipal admin =
        new AldebaranUserPrincipal(1L, null, "ADMIN", Collections.emptyList());
    when(securityService.isAdmin(admin)).thenReturn(true);

    boolean result = scoreSecurity.canView(10L, admin);

    assertTrue(result);
    verify(scoreRepository, never()).findById(any());
  }

  @Test
  @DisplayName("canView: Returns true if Score not found (delegates 404 to service)")
  void canView_NotFound_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.empty());

    boolean result = scoreSecurity.canView(10L, user);

    assertTrue(result);
  }

  @Test
  @DisplayName("canView: User can view their own score")
  void canView_MyScore_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScore score = WodScore.builder().id(10L).userId(2L).build(); // Same ID

    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    boolean result = scoreSecurity.canView(10L, user);

    assertTrue(result);
  }

  @Test
  @DisplayName("canView: User can view other's score on Public WOD")
  void canView_PublicWod_OtherScore_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    Wod publicWod = Wod.builder().isPublic(true).build();
    WodScore score = WodScore.builder().id(10L).userId(99L).wod(publicWod).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    boolean result = scoreSecurity.canView(10L, user);

    assertTrue(result);
  }

  @Test
  @DisplayName("canView: User can view other's score on same Gym WOD")
  void canView_GymWod_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    Wod gymWod = Wod.builder().isPublic(false).gymId(100L).build();
    WodScore score = WodScore.builder().id(10L).userId(99L).wod(gymWod).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    boolean result = scoreSecurity.canView(10L, user);

    assertTrue(result);
  }

  @Test
  @DisplayName("canView: User denied viewing score from different Gym")
  void canView_GymWod_DiffGym_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    Wod gymWod = Wod.builder().isPublic(false).gymId(200L).build(); // Diff Gym
    WodScore score = WodScore.builder().id(10L).userId(99L).wod(gymWod).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    boolean result = scoreSecurity.canView(10L, user);

    assertFalse(result);
  }

  @Test
  @DisplayName("canView: User denied viewing other's score on Private WOD")
  void canView_PrivateWod_OtherScore_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    Wod privateWod = Wod.builder().isPublic(false).gymId(null).authorId(99L).build();
    WodScore score = WodScore.builder().id(10L).userId(99L).wod(privateWod).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    boolean result = scoreSecurity.canView(10L, user);

    assertFalse(result);
  }

  // --- canCreate Checks ---

  @Test
  @DisplayName("canCreate: Admin is always allowed")
  void canCreate_Admin_ReturnsTrue() {
    AldebaranUserPrincipal admin =
        new AldebaranUserPrincipal(1L, null, "ADMIN", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            null, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null);

    when(securityService.isAdmin(admin)).thenReturn(true);

    assertTrue(scoreSecurity.canCreate(request, admin));
  }

  @Test
  @DisplayName("canCreate: Self-Log allowed on Public WOD")
  void canCreate_Self_Public_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            2L, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null);
    Wod publicWod = Wod.builder().isPublic(true).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(publicWod));

    assertTrue(scoreSecurity.canCreate(request, user));
  }

  @Test
  @DisplayName("canCreate: Self-Log allowed on Gym WOD (Same Gym)")
  void canCreate_Self_GymWod_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            null, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null); // Implied self
    Wod gymWod = Wod.builder().isPublic(false).gymId(100L).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(gymWod));

    assertTrue(scoreSecurity.canCreate(request, user));
  }

  @Test
  @DisplayName("canCreate: Self-Log denied on Gym WOD (Diff Gym)")
  void canCreate_Self_GymWod_DiffGym_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            2L, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null);
    Wod gymWod = Wod.builder().isPublic(false).gymId(200L).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(gymWod));

    assertFalse(scoreSecurity.canCreate(request, user));
  }

  @Test
  @DisplayName("canCreate: Self-Log allowed on Private WOD (Author)")
  void canCreate_Self_PrivateWod_Author_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            2L, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null);
    Wod privateWod = Wod.builder().isPublic(false).gymId(null).authorId(2L).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(privateWod));

    assertTrue(scoreSecurity.canCreate(request, user));
  }

  @Test
  @DisplayName("canCreate: Self-Log denied on Private WOD (Not Author)")
  void canCreate_Self_PrivateWod_NotAuthor_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            2L, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null);
    Wod privateWod = Wod.builder().isPublic(false).gymId(null).authorId(99L).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(privateWod));

    assertFalse(scoreSecurity.canCreate(request, user));
  }

  @Test
  @DisplayName("canCreate: Staff allowed to log for others in Same Gym")
  void canCreate_Staff_LogForOther_ReturnsTrue() {
    AldebaranUserPrincipal coach =
        new AldebaranUserPrincipal(2L, 100L, "COACH", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            99L, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null); // Target 99
    Wod gymWod = Wod.builder().gymId(100L).build();

    when(securityService.isAdmin(coach)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(gymWod));
    when(securityService.hasScoreVerificationRights(coach)).thenReturn(true);

    assertTrue(scoreSecurity.canCreate(request, coach));
  }

  @Test
  @DisplayName("canCreate: Staff denied logging for others if no verification rights")
  void canCreate_Staff_NoRights_ReturnsFalse() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            99L, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null);
    Wod gymWod = Wod.builder().gymId(100L).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(gymWod));
    when(securityService.hasScoreVerificationRights(user)).thenReturn(false);

    assertFalse(scoreSecurity.canCreate(request, user));
  }

  @Test
  @DisplayName("canCreate: Owner denied logging for others in Different Gym")
  void canCreate_Owner_DiffGym_ReturnsFalse() {
    AldebaranUserPrincipal owner =
        new AldebaranUserPrincipal(2L, 100L, "OWNER", Collections.emptyList());
    WodScoreRequest request =
        new WodScoreRequest(
            99L, 1L, null, 0, 0, null, null, null, null, null, null, null, null, null, false, null,
            null);
    Wod gymWod = Wod.builder().gymId(200L).build(); // Different Gym

    when(securityService.isAdmin(owner)).thenReturn(false);
    when(wodRepository.findById(1L)).thenReturn(Optional.of(gymWod));

    // Logic fails at gymId check before checking verification rights
    assertFalse(scoreSecurity.canCreate(request, owner));
  }

  // --- canModify Checks ---

  @Test
  @DisplayName("canModify: Admin is always allowed")
  void canModify_Admin_ReturnsTrue() {
    AldebaranUserPrincipal admin =
        new AldebaranUserPrincipal(1L, null, "ADMIN", Collections.emptyList());
    when(securityService.isAdmin(admin)).thenReturn(true);

    assertTrue(scoreSecurity.canModify(10L, admin));
  }

  @Test
  @DisplayName("canModify: Returns true if Score not found (delegates 404)")
  void canModify_NotFound_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.empty());

    assertTrue(scoreSecurity.canModify(10L, user));
  }

  @Test
  @DisplayName("canModify: User allowed to modify their own score")
  void canModify_Self_ReturnsTrue() {
    AldebaranUserPrincipal user =
        new AldebaranUserPrincipal(2L, 100L, "USER", Collections.emptyList());
    WodScore score = WodScore.builder().id(10L).userId(2L).build();

    when(securityService.isAdmin(user)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    assertTrue(scoreSecurity.canModify(10L, user));
  }

  @Test
  @DisplayName("canModify: Staff allowed to modify other's score in Same Gym")
  void canModify_Staff_SameGym_ReturnsTrue() {
    AldebaranUserPrincipal coach =
        new AldebaranUserPrincipal(2L, 100L, "COACH", Collections.emptyList());
    Wod gymWod = Wod.builder().gymId(100L).build();
    WodScore score = WodScore.builder().id(10L).userId(99L).wod(gymWod).build();

    when(securityService.isAdmin(coach)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));
    when(securityService.hasScoreVerificationRights(coach)).thenReturn(true);

    assertTrue(scoreSecurity.canModify(10L, coach));
  }

  @Test
  @DisplayName("canModify: Staff denied modifying score in Different Gym")
  void canModify_Staff_DiffGym_ReturnsFalse() {
    AldebaranUserPrincipal owner =
        new AldebaranUserPrincipal(2L, 100L, "OWNER", Collections.emptyList());
    Wod gymWod = Wod.builder().gymId(200L).build(); // Different Gym
    WodScore score = WodScore.builder().id(10L).userId(99L).wod(gymWod).build();

    when(securityService.isAdmin(owner)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    assertFalse(scoreSecurity.canModify(10L, owner));
  }

  @Test
  @DisplayName("canModify: Staff denied modifying score if no gym context (Private/Public WOD)")
  void canModify_Staff_NoGymContext_ReturnsFalse() {
    AldebaranUserPrincipal coach =
        new AldebaranUserPrincipal(2L, 100L, "COACH", Collections.emptyList());
    Wod publicWod = Wod.builder().gymId(null).isPublic(true).build();
    WodScore score = WodScore.builder().id(10L).userId(99L).wod(publicWod).build();

    when(securityService.isAdmin(coach)).thenReturn(false);
    when(scoreRepository.findById(10L)).thenReturn(Optional.of(score));

    assertFalse(scoreSecurity.canModify(10L, coach));
  }
}
