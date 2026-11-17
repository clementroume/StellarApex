package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.dto.MovementComponentDto;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.ScoreLineRequest;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.model.entities.ComponentTechnique;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.MovementRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoreLineServiceTest {

  private final MovementService movementService = mock(MovementService.class);
  private final MovementRepository movementRepository = mock(MovementRepository.class);
  private final ScoreLineService service = new ScoreLineService(movementService, movementRepository);

  @Test
  @DisplayName("A chained line keeps its component techniques when only its load changes")
  void chainedLineLighterLoad() {
    Movement chain = Movement.builder().id(1L).name("Clean and Jerk").build();
    WodMovement line =
        WodMovement.builder()
            .id(10L)
            .movement(chain)
            .orderIndex(1)
            .repsScheme("5")
            .weight(80.0)
            .weightUnit(Unit.KG)
            .equipment(new HashSet<>(Set.of("BARBELL")))
            .componentTechniques(
                new HashSet<>(
                    Set.of(new ComponentTechnique(1, "POWER"), new ComponentTechnique(2, "SPLIT"))))
            .build();
    Wod wod =
        Wod.builder().id(9L).scoreType(ScoreType.TIME).movements(new ArrayList<>(List.of(line))).build();

    MovementResponse chainDto = mock(MovementResponse.class);
    when(chainDto.name()).thenReturn("Clean and Jerk");
    when(chainDto.equipment()).thenReturn(Set.of(Equipment.BARBELL));
    when(chainDto.techniques()).thenReturn(Set.of());
    when(chainDto.components())
        .thenReturn(
            List.of(
                new MovementComponentDto(1, 2L, "Clean", true, Set.of(), Set.of()),
                new MovementComponentDto(2, 3L, "Jerk", true, Set.of(), Set.of())));
    MovementResponse clean = mock(MovementResponse.class);
    when(clean.techniques()).thenReturn(Set.of(Technique.POWER, Technique.SQUAT));
    MovementResponse jerk = mock(MovementResponse.class);
    when(jerk.techniques()).thenReturn(Set.of(Technique.SPLIT, Technique.PUSH));
    when(movementService.getMovement(1L)).thenReturn(chainDto);
    when(movementService.getMovement(2L)).thenReturn(clean);
    when(movementService.getMovement(3L)).thenReturn(jerk);
    when(movementRepository.getReferenceById(1L)).thenReturn(chain);

    ScoreRequest request =
        new ScoreRequest(
            null, LocalDate.now(), 9L, 5, 0, null, null, null, null, Unit.KG, null, null, null,
            ScalingLevel.SCALED, false, null, null, null, null, null, null,
            List.of(
                new ScoreLineRequest(
                    10L, 1L, Set.of("BARBELL"), Set.of(), null, 60.0, null, null, null, null, null,
                    null)),
            false, null);
    Score score = Score.builder().wod(wod).scaling(ScalingLevel.SCALED).build();

    assertDoesNotThrow(() -> service.applyLines(score, request, wod));
    assertEquals(1, score.getLines().size());
    assertEquals(60.0, score.getLines().getFirst().getWeightKg());
  }
}
