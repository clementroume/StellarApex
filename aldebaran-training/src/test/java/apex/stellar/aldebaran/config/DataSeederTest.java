package apex.stellar.aldebaran.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the public WOD seeding of {@link DataSeeder}. */
@ExtendWith(MockitoExtension.class)
class DataSeederTest {

  @Mock private MuscleRepository muscleRepository;
  @Mock private MovementRepository movementRepository;
  @Mock private WodRepository wodRepository;

  @InjectMocks private DataSeeder dataSeeder;

  @Test
  @DisplayName("Seeds public WODs from CSV, skipping rows with an unknown movement")
  @SuppressWarnings("unchecked")
  void shouldSeedPublicWods() throws Exception {
    when(movementRepository.findAll())
        .thenReturn(
            List.of(WodCsvCodecTest.THRUSTER, WodCsvCodecTest.PULL_UP, WodCsvCodecTest.RUNNING));

    dataSeeder.seedWods(
        new StringReader(WodCsvCodecTest.WODS_CSV),
        new StringReader(WodCsvCodecTest.WOD_MOVEMENTS_CSV));

    ArgumentCaptor<List<Wod>> captor = ArgumentCaptor.forClass(List.class);
    verify(wodRepository).saveAll(captor.capture());
    List<Wod> saved = captor.getValue();
    assertEquals(List.of("Fran", "Cindy"), saved.stream().map(Wod::getTitle).toList());
    assertTrue(saved.stream().allMatch(Wod::isPublic));
    assertTrue(saved.stream().allMatch(w -> w.getAuthorId() == null && w.getGymId() == null));
    assertEquals(2, saved.getFirst().getMovements().size());
  }

  @Test
  @DisplayName("Seeds muscles, then links members to their functional group by medical name")
  void shouldSeedMusclesWithFunctionalGroups() throws Exception {
    String csv =
        """
        medical_name,muscle_group,parent,common_name_en
        Rectus Femoris,LEGS,Quadriceps Femoris,
        Quadriceps Femoris,LEGS,,Quads
        Vastus Ghost,LEGS,Unknown Group,
        """;

    dataSeeder.seedMuscles(new StringReader(csv));

    ArgumentCaptor<Collection<Muscle>> captor = ArgumentCaptor.captor();
    verify(muscleRepository).saveAll(captor.capture());
    Map<String, Muscle> saved =
        captor.getValue().stream().collect(Collectors.toMap(Muscle::getMedicalName, m -> m));
    assertEquals(3, saved.size());
    assertSame(saved.get("Quadriceps Femoris"), saved.get("Rectus Femoris").getParent());
    assertNull(saved.get("Quadriceps Femoris").getParent());
    assertNull(saved.get("Vastus Ghost").getParent());
  }

  @Test
  @DisplayName("Does not seed WODs when a public WOD already exists")
  void shouldNotSeedWhenPublicWodsExist() throws Exception {
    when(muscleRepository.count()).thenReturn(1L);
    when(movementRepository.count()).thenReturn(1L);
    when(wodRepository.existsPublic()).thenReturn(true);

    dataSeeder.run();

    verify(wodRepository, never()).saveAll(any());
  }
}
