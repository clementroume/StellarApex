package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatically seeds the database with foundational catalog data if tables are empty: muscles,
 * movements, then public benchmark WODs (temporary git-ignored CSV backups, see {@link
 * WodCsvCodec}). Disabled with {@code aldebaran.seed.enabled=false} (integration tests, so they never
 * depend on the developer's local CSV files).
 */
@Component
@ConditionalOnProperty(name = "aldebaran.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

  private final MuscleRepository muscleRepository;
  private final MovementRepository movementRepository;
  private final WodRepository wodRepository;

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

    if (!wodRepository.existsPublic()) {
      log.info("No public WOD: Seeding benchmark WODs...");
      seedWods();
    }
  }

  private void seedMuscles() {
    ClassPathResource resource = new ClassPathResource("muscles.csv");

    if (!resource.exists()) {
      log.warn("muscle.csv file doesn't exist, seeding aborted");
      return;
    }

    try (Reader reader = new InputStreamReader(resource.getInputStream())) {
      seedMuscles(reader);
      log.info("Successfully seeded Muscles.");
    } catch (Exception e) {
      log.error("Failed to seed muscles", e);
    }
  }

  /**
   * Parses the muscle CSV content, saves the muscles, then links each one to its functional group
   * ({@code parent} column, by medical name; unknown names are logged and left unlinked).
   *
   * @param reader Content of {@code muscles.csv}.
   * @throws IOException If the content cannot be read.
   */
  void seedMuscles(Reader reader) throws IOException {
    Iterable<CSVRecord> records =
        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader);

    // Functional groups are linked once every muscle is saved (the file order is free)
    Map<String, Muscle> byName = new LinkedHashMap<>();
    Map<Muscle, String> parentNames = new LinkedHashMap<>();
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
      byName.put(muscle.getMedicalName(), muscle);
      String parentName = getOptionalStr(csvRecord, "parent");
      if (parentName != null) {
        parentNames.put(muscle, parentName);
      }
    }
    muscleRepository.saveAll(byName.values());
    // Managed entities: the links are flushed with the seeding transaction
    parentNames.forEach(
        (muscle, parentName) -> {
          Muscle parent = byName.get(parentName);
          if (parent == null) {
            log.warn(
                "Unknown functional group '{}' for muscle '{}'",
                parentName,
                muscle.getMedicalName());
          }
          muscle.setParent(parent);
        });
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

  private void seedWods() {
    ClassPathResource wodsResource = new ClassPathResource(WodCsvCodec.WODS_FILE);
    ClassPathResource movementsResource = new ClassPathResource(WodCsvCodec.WOD_MOVEMENTS_FILE);

    if (!wodsResource.exists() || !movementsResource.exists()) {
      log.warn(
          "{} or {} doesn't exist, seeding aborted",
          WodCsvCodec.WODS_FILE,
          WodCsvCodec.WOD_MOVEMENTS_FILE);
      return;
    }

    try (Reader wods =
            new InputStreamReader(wodsResource.getInputStream(), StandardCharsets.UTF_8);
        Reader wodMovements =
            new InputStreamReader(movementsResource.getInputStream(), StandardCharsets.UTF_8)) {
      seedWods(wods, wodMovements);
    } catch (Exception e) {
      log.error("Failed to seed WODs", e);
    }
  }

  /**
   * Parses both WOD CSV contents and saves the public WODs.
   *
   * @param wods Content of {@code wods.csv}.
   * @param wodMovements Content of {@code wod_movements.csv}.
   * @throws IOException If a content cannot be read.
   */
  void seedWods(Reader wods, Reader wodMovements) throws IOException {
    List<Wod> parsed =
        WodCsvCodec.read(wods, wodMovements, WodCsvCodec.byName(movementRepository.findAll()));
    wodRepository.saveAll(parsed);
    log.info("Successfully seeded {} public WODs.", parsed.size());
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
