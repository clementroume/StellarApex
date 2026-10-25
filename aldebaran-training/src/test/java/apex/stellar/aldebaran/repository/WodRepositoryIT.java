package apex.stellar.aldebaran.repository;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.projection.WodSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Integration tests for {@link WodRepository#findSecure}, run against PostgreSQL to validate the
 * typing of optional (null) parameters and the scope/security clauses.
 */
class WodRepositoryIT extends BaseIntegrationTest {

  private static final Long ME = 10L;
  private static final Set<Long> MY_GYMS = Set.of(101L, 300L);
  private static final Pageable PAGE = PageRequest.of(0, 50, Sort.by("title"));

  @Autowired private WodRepository wodRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private ScoreRepository scoreRepository;

  private Movement pullUp;

  @BeforeEach
  void setUp() {
    scoreRepository.deleteAll();
    wodRepository.deleteAll();
    movementRepository.deleteAll();

    pullUp = movementRepository.save(
        Movement.builder().name("Pull-up").category(Category.PULLING).build());

    save("Box WOD", WodType.FOR_TIME, 101L, 50L, false, true);
    save("Virtual WOD", WodType.AMRAP, 300L, 51L, false, false);
    save("Other Gym WOD", WodType.AMRAP, 999L, 52L, false, false);
    save("Other Gym Public", WodType.FOR_TIME, 999L, 52L, true, false);
    save("My Personal", WodType.EMOM, null, ME, false, false);
    save("Stranger Public", WodType.AMRAP, null, 77L, true, true);
    save("Stranger Private", WodType.AMRAP, null, 77L, false, false);
  }

  private void save(
      String title, WodType type, Long gymId, Long authorId, boolean isPublic, boolean pullUps) {
    Wod wod =
        Wod.builder()
            .title(title)
            .wodType(type)
            .scoreType(ScoreType.TIME)
            .gymId(gymId)
            .authorId(authorId)
            .isPublic(isPublic)
            .movements(new ArrayList<>())
            .build();
    if (pullUps) {
      WodMovement wm = new WodMovement();
      wm.setWod(wod);
      wm.setMovement(pullUp);
      wm.setOrderIndex(1);
      wod.getMovements().add(wm);
    }
    wodRepository.save(wod);
  }

  private List<String> titles(
      String search, WodType type, Long movementId, String scope, Long gymId, boolean admin) {
    return wodRepository
        .findSecure(search, type, movementId, scope, gymId, null, ME, MY_GYMS, admin, PAGE)
        .map(WodSummary::getTitle)
        .getContent();
  }

  @Test
  @DisplayName("ALL: public + mine + member gyms, never other gyms' private WODs")
  void scopeAll() {
    assertThat(titles(null, null, null, "ALL", null, false))
        .containsExactly(
            "Box WOD", "My Personal", "Other Gym Public", "Stranger Public", "Virtual WOD");
  }

  @Test
  @DisplayName("ALL as admin: everything")
  void scopeAll_Admin() {
    assertThat(titles(null, null, null, "ALL", null, true)).hasSize(7);
  }

  @Test
  @DisplayName("MINE: personal WODs authored by the user only")
  void scopeMine() {
    assertThat(titles(null, null, null, "MINE", null, false)).containsExactly("My Personal");
  }

  @Test
  @DisplayName("GYM: every member gym, then narrowed to one; non-member gymId yields nothing")
  void scopeGym() {
    assertThat(titles(null, null, null, "GYM", null, false))
        .containsExactly("Box WOD", "Virtual WOD");
    assertThat(titles(null, null, null, "GYM", 300L, false)).containsExactly("Virtual WOD");
    // Gym 999 is not a member gym: its public WOD must not leak into the GYM scope
    assertThat(titles(null, null, null, "GYM", 999L, false)).isEmpty();
  }

  @Test
  @DisplayName("GYM as admin: any gym can be targeted")
  void scopeGym_Admin() {
    assertThat(titles(null, null, null, "GYM", 999L, true))
        .containsExactly("Other Gym Public", "Other Gym WOD");
  }

  @Test
  @DisplayName("PUBLIC: public WODs only")
  void scopePublic() {
    assertThat(titles(null, null, null, "PUBLIC", null, false))
        .containsExactly("Other Gym Public", "Stranger Public");
  }

  @Test
  @DisplayName("Search, type and movement filters combine")
  void combinedFilters() {
    assertThat(titles("%wod%", WodType.AMRAP, null, "ALL", null, false))
        .containsExactly("Virtual WOD");
    assertThat(titles(null, null, pullUp.getId(), "ALL", null, false))
        .containsExactly("Box WOD", "Stranger Public");
    assertThat(titles("%box%", WodType.FOR_TIME, pullUp.getId(), "GYM", 101L, false))
        .containsExactly("Box WOD");
  }

  @Test
  @DisplayName("Projection exposes authorId, gymId and isPublic")
  void projectionFields() {
    WodSummary box =
        wodRepository
            .findSecure("%box%", null, null, "ALL", null, null, ME, MY_GYMS, false, PAGE)
            .getContent()
            .getFirst();

    assertThat(box.getAuthorId()).isEqualTo(50L);
    assertThat(box.getGymId()).isEqualTo(101L);
    assertThat(box.isPublic()).isFalse();
  }

  @Test
  @DisplayName("authorId: only that author's personal WODs that the user can read")
  void authorFilter() {
    // A stranger's private WOD stays hidden to non-admins, even when filtering on its author
    assertThat(
            wodRepository
                .findSecure(null, null, null, "ALL", null, 77L, ME, MY_GYMS, false, PAGE)
                .map(WodSummary::getTitle)
                .getContent())
        .containsExactly("Stranger Public");
    assertThat(
            wodRepository
                .findSecure(null, null, null, "ALL", null, 77L, ME, MY_GYMS, true, PAGE)
                .map(WodSummary::getTitle)
                .getContent())
        .containsExactly("Stranger Private", "Stranger Public");
    // Gym WODs are never "personal", even for their author
    assertThat(
            wodRepository
                .findSecure(null, null, null, "ALL", null, 50L, ME, MY_GYMS, true, PAGE)
                .getContent())
        .isEmpty();
  }

  @Test
  @DisplayName("facets: types follow the scope, gyms and authors follow the security clause")
  void facets() {
    assertThat(
            wodRepository.findSecureWodTypes(
                null, null, null, "GYM", 101L, null, ME, MY_GYMS, false))
        .containsExactly(WodType.FOR_TIME);
    assertThat(
            wodRepository.findSecureWodTypes(
                null, null, null, "PUBLIC", null, null, ME, MY_GYMS, false))
        .containsExactlyInAnyOrder(WodType.FOR_TIME, WodType.AMRAP);

    assertThat(
            wodRepository.findSecureGymIds(null, null, null, "ALL", null, null, ME, MY_GYMS, false))
        .containsExactlyInAnyOrder(101L, 300L, 999L);
    assertThat(
            wodRepository.findSecureAuthorIds(
                null, null, null, "ALL", null, null, ME, MY_GYMS, false))
        .containsExactlyInAnyOrder(ME, 77L);
    assertThat(
            wodRepository.findSecureAuthorIds(
                null, null, null, "ALL", null, null, ME, MY_GYMS, true))
        .containsExactlyInAnyOrder(ME, 77L);
  }
}
