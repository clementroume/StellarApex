package apex.stellar.aldebaran.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.config.WodCsvCodec;
import apex.stellar.aldebaran.config.WodCsvCodecTestSupport;
import apex.stellar.aldebaran.controller.Exporter.CsvExportService;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the WOD CSV export (always into a temporary directory). */
@ExtendWith(MockitoExtension.class)
class CsvExportServiceTest {

  @Mock private MuscleRepository muscleRepository;
  @Mock private MovementRepository movementRepository;
  @Mock private WodRepository wodRepository;

  @InjectMocks private CsvExportService exportService;

  @TempDir Path tempDir;

  @Test
  @DisplayName("Export then re-seed of public WODs is lossless")
  void shouldRoundTripPublicWods() throws Exception {
    List<Wod> wods = WodCsvCodecTestSupport.sampleWods();
    when(wodRepository.findAllPublicWithMovements()).thenReturn(wods);

    exportService.exportWods(tempDir);

    Path wodsFile = tempDir.resolve(WodCsvCodec.WODS_FILE);
    Path movementsFile = tempDir.resolve(WodCsvCodec.WOD_MOVEMENTS_FILE);
    assertTrue(Files.exists(wodsFile));
    assertTrue(Files.exists(movementsFile));

    try (Reader w = Files.newBufferedReader(wodsFile, StandardCharsets.UTF_8);
        Reader m = Files.newBufferedReader(movementsFile, StandardCharsets.UTF_8)) {
      List<Wod> read = WodCsvCodec.read(w, m, WodCsvCodecTestSupport.catalog());
      assertEquals(wods.size(), read.size());
      for (int i = 0; i < wods.size(); i++) {
        WodCsvCodecTestSupport.assertSameWod(wods.get(i), read.get(i));
        assertEquals(wods.get(i).getModalities(), read.get(i).getModalities());
      }
    }
  }
}
