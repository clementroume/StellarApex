package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.dto.WodCapabilities;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.GymAccess;
import apex.stellar.aldebaran.security.SecurityService;
import apex.stellar.aldebaran.security.WodSecurity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WodCapabilityServiceTest {

  @Mock private WodService wodService;
  @Mock private ScoreRepository scoreRepository;

  private WodCapabilityService capabilityService;

  @BeforeEach
  void setUp() {
    // Real security rules (no mocks) to test the end-to-end capability computation
    WodSecurity wodSecurity = new WodSecurity(new SecurityService(), wodService);
    capabilityService = new WodCapabilityService(wodSecurity, scoreRepository);
  }

  private static WodResponse gymWod(Long gymId) {
    return new WodResponse(
        10L, "Fran", null, null, null, 50L, gymId, false, null, null, null, null, null, null, null, null, Set.of(),
        List.of(), null, null, null);
  }

  private static AldebaranUserPrincipal member(String gymRole, List<String> permissions) {
    return AldebaranUserPrincipal.builder()
        .id(7L)
        .platformRole("USER")
        .memberships(Map.of(101L, new GymAccess(gymRole, Set.copyOf(permissions))))
        .build();
  }

  @Test
  @DisplayName("Owner (all permissions) can modify a gym WOD")
  void owner_CanModify() {
    when(scoreRepository.existsByWodId(10L)).thenReturn(false);

    WodCapabilities caps =
        capabilityService
            .withCapabilities(
                gymWod(101L), member("OWNER", List.of("WOD_WRITE", "SCORE_VERIFY")))
            .capabilities();

    assertTrue(caps.canModify());
    assertFalse(caps.locked());
  }

  @Test
  @DisplayName("Coach without WOD_WRITE cannot modify a gym WOD")
  void coachWithoutPermission_CannotModify() {
    when(scoreRepository.existsByWodId(10L)).thenReturn(false);

    WodCapabilities caps =
        capabilityService
            .withCapabilities(gymWod(101L), member("COACH", List.of("SCORE_VERIFY")))
            .capabilities();

    assertFalse(caps.canModify());
  }

  @Test
  @DisplayName("WOD with scores is reported as locked")
  void wodWithScores_IsLocked() {
    when(scoreRepository.existsByWodId(10L)).thenReturn(true);

    WodCapabilities caps =
        capabilityService
            .withCapabilities(gymWod(101L), member("OWNER", List.of("WOD_WRITE")))
            .capabilities();

    assertTrue(caps.canModify());
    assertTrue(caps.locked());
  }

  @Test
  @DisplayName("Coach with WOD_WRITE in gym A cannot modify a WOD of gym B where they lack it")
  void coachPermissionIsPerGym() {
    when(scoreRepository.existsByWodId(10L)).thenReturn(false);
    AldebaranUserPrincipal coach =
        AldebaranUserPrincipal.builder()
            .id(7L)
            .platformRole("USER")
            .memberships(
                Map.of(
                    101L, new GymAccess("COACH", Set.of("WOD_WRITE")),
                    202L, new GymAccess("ATHLETE", Set.of())))
            .build();

    assertTrue(capabilityService.withCapabilities(gymWod(101L), coach).capabilities().canModify());
    assertFalse(
        capabilityService.withCapabilities(gymWod(202L), coach).capabilities().canModify());
  }
}
