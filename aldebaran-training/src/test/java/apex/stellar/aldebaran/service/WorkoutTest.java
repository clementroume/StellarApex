package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodScaledLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Workout: prescribed blocks of a programming (lot D', D162)")
class WorkoutTest {

  @Test
  @DisplayName("Scaled reads the official Scaled version, a line it keeps stays RX")
  void scaled() {
    Movement pullUp = Movement.builder().id(1L).name("Pull-up").build();
    Movement ringRow = Movement.builder().id(2L).name("Ring Row").build();
    Movement squat = Movement.builder().id(3L).name("Air Squat").build();
    WodMovement first = WodMovement.builder().id(10L).orderIndex(0).movement(pullUp).repsScheme("10").build();
    first.setScaled(WodScaledLine.builder().wodMovement(first).movement(ringRow).repsScheme("8").build());
    WodMovement second = WodMovement.builder().id(11L).orderIndex(1).movement(squat).repsScheme("20").build();
    Wod wod = Wod.builder().id(5L).movements(new ArrayList<>(List.of(first, second))).build();
    LocalDate day = LocalDate.of(2026, 11, 18);

    List<WodMovement> scaled = Workout.prescribed(wod, day, Kind.WOD, true).lines();
    assertThat(scaled.get(0).getMovement()).isEqualTo(ringRow);
    assertThat(scaled.get(0).getRepsScheme()).isEqualTo("8");
    assertThat(scaled.get(1)).isSameAs(second);

    List<WodMovement> rx = Workout.prescribed(wod, day, Kind.WOD, false).lines();
    assertThat(rx.get(0)).isSameAs(first);
    assertThat(Workout.prescribed(wod, day, Kind.WOD, true).score()).isNull();
  }
}
