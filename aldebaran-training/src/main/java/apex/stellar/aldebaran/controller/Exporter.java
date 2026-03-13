package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aldebaran/export")
@RequiredArgsConstructor
public class Exporter {

  private final CsvExportService exportService;

  @PostMapping("/muscles")
  public ResponseEntity<String> exportMuscles() {

    exportService.exportMuscles();
    return ResponseEntity.ok("Export complet Muscles réussi et fichiers écrasés !");
  }

  @PostMapping("/movements")
  public ResponseEntity<String> exportMovements() {

    exportService.exportMovements();
    return ResponseEntity.ok("Export complet Mouvements réussi et fichiers écrasés !");
  }

  // --- Inner Service Class ---

  @Service
  @RequiredArgsConstructor
  @Slf4j
  public static class CsvExportService {

    private final MuscleRepository muscleRepository;
    private final MovementRepository movementRepository;

    @Transactional(readOnly = true)
    public void exportMuscles() {
      List<Muscle> muscles = muscleRepository.findAll();
      String[] headers = {
        "medical_name",
        "muscle_group",
        "common_name_en",
        "common_name_fr",
        "description_en",
        "description_fr",
        "image_url"
      };

      String musclesCsvPath = "src/main/resources/muscles.csv";
      try (FileWriter out = new FileWriter(musclesCsvPath);
          CSVPrinter printer =
              new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(headers).get())) {

        for (Muscle m : muscles) {
          printer.printRecord(
              m.getMedicalName(),
              m.getMuscleGroup().name(),
              m.getCommonNameEn(),
              m.getCommonNameFr(),
              m.getDescriptionEn(),
              m.getDescriptionFr(),
              m.getImageUrl());
        }
        log.info("Successfully exported {} muscles to {}", muscles.size(), musclesCsvPath);

      } catch (IOException e) {
        log.error("Failed to export muscles to CSV", e);
        throw new RuntimeException("Erreur lors de l'export des muscles", e);
      }
    }

    @Transactional(readOnly = true)
    public void exportMovements() {
      List<Movement> movements = movementRepository.findAll();
      String[] headers = {
        "name",
        "name_abbreviation",
        "category",
        "equipment",
        "techniques",
        "agonists",
        "synergists",
        "stabilizers",
        "description_en",
        "description_fr",
        "coaching_cues_en",
        "coaching_cues_fr",
        "video_url",
        "image_url"
      };

      String movementsCsvPath = "src/main/resources/movements.csv";
      try (FileWriter out = new FileWriter(movementsCsvPath);
          CSVPrinter printer =
              new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(headers).get())) {

        for (Movement m : movements) {
          String equipment =
              m.getEquipment().stream().map(Enum::name).collect(Collectors.joining(", "));

          String techniques =
              m.getTechniques().stream().map(Enum::name).collect(Collectors.joining(", "));

          String agonists = filterMusclesByRole(m, MovementMuscle.MuscleRole.AGONIST);
          String synergists = filterMusclesByRole(m, MovementMuscle.MuscleRole.SYNERGIST);
          String stabilizers = filterMusclesByRole(m, MovementMuscle.MuscleRole.STABILIZER);

          printer.printRecord(
              m.getName(),
              m.getNameAbbreviation(),
              m.getCategory(),
              equipment,
              techniques,
              agonists,
              synergists,
              stabilizers,
              m.getDescriptionEn(),
              m.getDescriptionFr(),
              m.getCoachingCuesEn(),
              m.getCoachingCuesFr(),
              m.getVideoUrl(),
              m.getImageUrl());
        }
        log.info("Successfully exported {} movements to {}", movements.size(), movementsCsvPath);

      } catch (IOException e) {
        log.error("Failed to export movements to CSV", e);
        throw new RuntimeException("Erreur lors de l'export des mouvements", e);
      }
    }

    private String filterMusclesByRole(Movement m, MovementMuscle.MuscleRole role) {
      return m.getTargetedMuscles().stream()
          .filter(tm -> tm.getRole() == role)
          .map(tm -> tm.getMuscle().getMedicalName())
          .collect(Collectors.joining(", "));
    }
  }
}
