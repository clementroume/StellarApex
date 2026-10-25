package apex.stellar.aldebaran.controller;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.controller.Exporter.CsvExportService;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for the catalog CSV export against a real database.
 *
 * <p>The sort properties are validated by Spring Data against the JPA entities, which mocked
 * repositories cannot check. Files are always written into a temporary directory.
 */
class CsvExportServiceIT extends BaseIntegrationTest {

  @Autowired private CsvExportService exportService;
  @Autowired private MuscleRepository muscleRepository;

  @TempDir Path tempDir;

  @Test
  @DisplayName("exportMuscles: should write every muscle with its image URL")
  void testExportMuscles_WritesImageUrl() throws Exception {
    muscleRepository.save(
        Muscle.builder()
            .medicalName("Adductor Test Group")
            .muscleGroup(MuscleGroup.LEGS)
            .imageUrl("https://example.com/adductors.png")
            .build());

    exportService.exportMuscles(tempDir);

    String csv = Files.readString(tempDir.resolve("muscles.csv"), StandardCharsets.UTF_8);
    assertThat(csv).startsWith("medical_name,muscle_group,");
    assertThat(csv).contains("Adductor Test Group,LEGS,").contains("https://example.com/adductors.png");
  }

  @Test
  @DisplayName("exportMovements: should write the movement catalog")
  void testExportMovements_WritesCatalog() throws Exception {
    exportService.exportMovements(tempDir);

    assertThat(tempDir.resolve("movements.csv")).exists();
    assertThat(Files.readString(tempDir.resolve("movements.csv"), StandardCharsets.UTF_8))
        .startsWith("name,name_abbreviation,category,");
  }
}
