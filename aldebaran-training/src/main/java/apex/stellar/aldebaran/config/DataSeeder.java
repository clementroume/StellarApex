package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Automatically seeds the database with foundational catalog data if tables are empty. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

  private final MuscleRepository muscleRepository;
  private final MovementRepository movementRepository;

  @Override
  @Transactional
  public void run(String @NonNull ... args) {
    if (muscleRepository.count() == 0) {
      log.info("Database empty: Seeding Muscles...");
      seedMuscles();
    }

    if (movementRepository.count() == 0) {
      log.info("Database empty: Seeding Movements...");
      seedMovements();
    }
  }

  private void seedMuscles() {
    ClassPathResource resource = new ClassPathResource("muscles.csv");

    if (!resource.exists()) {
      log.warn("muscle.csv file doesn't exist, seeding aborted");
      return;
    }

    try (Reader reader = new InputStreamReader(resource.getInputStream())) {
      Iterable<CSVRecord> records =
          CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader);

      for (CSVRecord csvRecord : records) {
        Muscle muscle =
            Muscle.builder()
                .medicalName(csvRecord.get("medical_name").trim())
                .muscleGroup(
                    MuscleGroup.valueOf(csvRecord.get("muscle_group").trim().toUpperCase()))
                .commonNameEn(getOptionalStr(csvRecord, "common_name_en"))
                .commonNameFr(getOptionalStr(csvRecord, "common_name_fr"))
                .descriptionEn(getOptionalStr(csvRecord, "description_en"))
                .descriptionFr(getOptionalStr(csvRecord, "description_fr"))
                .imageUrl(getOptionalStr(csvRecord, "image_url"))
                .build();
        muscleRepository.save(muscle);
      }
      log.info("Successfully seeded Muscles.");
    } catch (Exception e) {
      log.error("Failed to seed muscles", e);
    }
  }

  private void seedMovements() {
    Map<String, Muscle> muscleCache =
        muscleRepository.findAll().stream()
            .collect(Collectors.toMap(m -> m.getMedicalName().toLowerCase(), m -> m));

    ClassPathResource resource = new ClassPathResource("movements.csv");

    if (!resource.exists()) {
      log.warn("movements.csv file doesn't exist, seeding aborted");
      return;
    }

    try (Reader reader = new InputStreamReader(resource.getInputStream())) {
      Iterable<CSVRecord> records =
          CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader);

      for (CSVRecord csvRecord : records) {
        Movement movement =
            Movement.builder()
                .name(csvRecord.get("name").trim())
                .nameAbbreviation(getOptionalStr(csvRecord, "name_abbreviation"))
                .category(Category.valueOf(csvRecord.get("category").trim().toUpperCase()))
                .equipment(parseEnums(csvRecord.get("equipment"), Equipment.class))
                .techniques(parseEnums(csvRecord.get("techniques"), Technique.class))
                .descriptionEn(getOptionalStr(csvRecord, "description_en"))
                .descriptionFr(getOptionalStr(csvRecord, "description_fr"))
                .coachingCuesEn(getOptionalStr(csvRecord, "coaching_cues_en"))
                .coachingCuesFr(getOptionalStr(csvRecord, "coaching_cues_fr"))
                .videoUrl(getOptionalStr(csvRecord, "video_url"))
                .imageUrl(getOptionalStr(csvRecord, "image_url"))
                .build();

        // Ajout des relations musculaires
        addMusclesToMovement(
            movement, csvRecord.get("agonists"), MuscleRole.AGONIST, 1.0, muscleCache);
        addMusclesToMovement(
            movement, csvRecord.get("synergists"), MuscleRole.SYNERGIST, 0.5, muscleCache);
        addMusclesToMovement(
            movement, csvRecord.get("stabilizers"), MuscleRole.STABILIZER, 0.2, muscleCache);

        movementRepository.save(movement);
      }
      log.info("Successfully seeded Movements.");
    } catch (Exception e) {
      log.error("Failed to seed movements", e);
    }
  }

  // --- Helper Methods ---

  private String getOptionalStr(CSVRecord csvRecord, String header) {
    if (!csvRecord.isMapped(header)
        || csvRecord.get(header) == null
        || csvRecord.get(header).isBlank()) {
      return null;
    }
    return csvRecord.get(header).trim();
  }

  private <E extends Enum<E>> Set<E> parseEnums(String csvField, Class<E> enumClass) {
    if (csvField == null || csvField.isBlank()) {
      return Set.of();
    }

    return Arrays.stream(csvField.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> Enum.valueOf(enumClass, s.toUpperCase()))
        .collect(Collectors.toSet());
  }

  private void addMusclesToMovement(
      Movement movement,
      String csvField,
      MuscleRole role,
      Double impact,
      Map<String, Muscle> cache) {
    if (csvField == null || csvField.isBlank()) {
      return;
    }

    Arrays.stream(csvField.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .forEach(
            muscleName -> {
              Muscle muscle = cache.get(muscleName.toLowerCase());
              if (muscle != null) {
                movement
                    .getTargetedMuscles()
                    .add(
                        MovementMuscle.builder()
                            .movement(movement)
                            .muscle(muscle)
                            .role(role)
                            .impactFactor(impact)
                            .build());
              } else {
                log.warn("Muscle not found in DB during seeding: {}", muscleName);
              }
            });
  }
}
