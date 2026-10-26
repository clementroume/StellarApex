package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Unit;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Temporary development tool: reads and writes the public benchmark WODs as the two git-ignored
 * seed CSV files ({@code wods.csv} and {@code wod_movements.csv}).
 *
 * <p>WODs are keyed by title, movements by their exact catalog name. Blank cells map to {@code
 * null} and {@code null} values to blank cells, so a database → CSV → database round-trip is
 * lossless. WOD modalities are not stored: they are recomputed from the movements' categories.
 */
@Slf4j
public final class WodCsvCodec {

  /** WOD file name in {@code src/main/resources}. */
  public static final String WODS_FILE = "wods.csv";

  /** WOD movement file name in {@code src/main/resources}. */
  public static final String WOD_MOVEMENTS_FILE = "wod_movements.csv";

  static final String[] WOD_HEADERS = {
    "title",
    "wod_type",
    "category",
    "score_type",
    "rep_scheme",
    "time_cap_seconds",
    "emom_interval",
    "emom_rounds",
    "description_en",
    "description_fr",
    "notes_en",
    "notes_fr"
  };

  static final String[] WOD_MOVEMENT_HEADERS = {
    "wod_title",
    "order_index",
    "movement_name",
    "reps_scheme",
    "weight",
    "weight_unit",
    "distance",
    "distance_unit",
    "duration_seconds",
    "calories",
    "notes_en",
    "scaling_options_en",
    // Optional trailing columns, only needed to keep values edited in Sirius
    "duration_display_unit",
    "equipment",
    "techniques",
    "weight_female",
    "notes_fr",
    "scaling_options_fr",
    "calories_female",
    "height",
    "height_female",
    "height_unit"
  };

  /** Separator of the equipment / technique lists (free text, so not a comma). */
  private static final String LIST_SEPARATOR = "|";

  private WodCsvCodec() {}

  /**
   * Parses both CSV files into public WOD entities (not saved).
   *
   * <p>A movement row whose movement name is unknown, or whose WOD title is not in the WOD file,
   * is logged and skipped. A WOD keeps its other movements.
   *
   * @param wodsReader Content of {@code wods.csv}.
   * @param wodMovementsReader Content of {@code wod_movements.csv}.
   * @param movementsByName Catalog movements keyed by exact name.
   * @return The WODs, in file order, with {@code isPublic = true} and no author or gym.
   * @throws IOException If a file cannot be read.
   * @throws apex.stellar.aldebaran.exception.InvalidWodMovementException If a WOD movement uses an
   *     equipment or a technique that its movement does not allow.
   */
  public static List<Wod> read(
      Reader wodsReader, Reader wodMovementsReader, Map<String, Movement> movementsByName)
      throws IOException {
    Map<String, Wod> wodsByTitle = new LinkedHashMap<>();
    for (CSVRecord row : parse(wodsReader)) {
      String title = required(row, "title");
      Wod wod =
          Wod.builder()
              .title(title)
              .wodType(WodType.valueOf(required(row, "wod_type").toUpperCase()))
              .category(category(row))
              .scoreType(ScoreType.valueOf(required(row, "score_type").toUpperCase()))
              .repScheme(text(row, "rep_scheme"))
              .timeCapSeconds(integer(row, "time_cap_seconds"))
              .emomInterval(integer(row, "emom_interval"))
              .emomRounds(integer(row, "emom_rounds"))
              .descriptionEn(text(row, "description_en", "description"))
              .descriptionFr(text(row, "description_fr"))
              .notesEn(text(row, "notes_en", "notes"))
              .notesFr(text(row, "notes_fr"))
              .isPublic(true)
              .authorId(null)
              .gymId(null)
              .modalities(new HashSet<>())
              .movements(new ArrayList<>())
              .build();
      if (wodsByTitle.putIfAbsent(title, wod) != null) {
        log.warn("Duplicate WOD title '{}' in {}, row skipped", title, WODS_FILE);
      }
    }

    for (CSVRecord row : parse(wodMovementsReader)) {
      String wodTitle = required(row, "wod_title");
      String movementName = required(row, "movement_name");
      Wod wod = wodsByTitle.get(wodTitle);
      if (wod == null) {
        log.warn("Unknown WOD '{}' in {}, row skipped", wodTitle, WOD_MOVEMENTS_FILE);
        continue;
      }
      Movement movement = movementsByName.get(movementName);
      if (movement == null) {
        log.warn(
            "Unknown movement '{}' for WOD '{}' in {}, row skipped",
            movementName,
            wodTitle,
            WOD_MOVEMENTS_FILE);
        continue;
      }
      WodMovement wodMovement =
          WodMovement.builder()
              .wod(wod)
              .movement(movement)
              .orderIndex(Integer.parseInt(required(row, "order_index")))
              .repsScheme(text(row, "reps_scheme"))
              .weight(decimal(row, "weight"))
              .weightUnit(unit(row, "weight_unit"))
              .distance(decimal(row, "distance"))
              .distanceUnit(unit(row, "distance_unit"))
              .durationSeconds(integer(row, "duration_seconds"))
              .durationDisplayUnit(unit(row, "duration_display_unit"))
              .calories(integer(row, "calories"))
              .weightFemale(decimal(row, "weight_female"))
              .notesEn(text(row, "notes_en", "notes"))
              .notesFr(text(row, "notes_fr"))
              .scalingOptionsEn(text(row, "scaling_options_en", "scaling_options"))
              .scalingOptionsFr(text(row, "scaling_options_fr"))
              .caloriesFemale(integer(row, "calories_female"))
              .height(decimal(row, "height"))
              .heightFemale(decimal(row, "height_female"))
              .heightUnit(unit(row, "height_unit"))
              .equipment(list(row, "equipment"))
              .techniques(list(row, "techniques"))
              .build();
      wodMovement.checkVariantsAllowed(
          movement.getName(), movement.getEquipment(), movement.getTechniques());
      wod.getMovements().add(wodMovement);
      if (movement.getCategory() != null && movement.getCategory().getModality() != null) {
        wod.getModalities().add(movement.getCategory().getModality());
      }
    }

    wodsByTitle
        .values()
        .forEach(w -> w.getMovements().sort(Comparator.comparing(WodMovement::getOrderIndex)));
    return new ArrayList<>(wodsByTitle.values());
  }

  /**
   * Writes WODs (with their movements) into both CSV files.
   *
   * @param wods The WODs to write, in output order; movements are written by order index.
   * @param wodsWriter Target of {@code wods.csv}.
   * @param wodMovementsWriter Target of {@code wod_movements.csv}.
   * @throws IOException If a file cannot be written.
   */
  public static void write(Collection<Wod> wods, Writer wodsWriter, Writer wodMovementsWriter)
      throws IOException {
    try (CSVPrinter wodPrinter = printer(wodsWriter, WOD_HEADERS);
        CSVPrinter movementPrinter = printer(wodMovementsWriter, WOD_MOVEMENT_HEADERS)) {
      for (Wod w : wods) {
        wodPrinter.printRecord(
            w.getTitle(),
            w.getWodType().name(),
            name(w.getCategory()),
            w.getScoreType().name(),
            w.getRepScheme(),
            w.getTimeCapSeconds(),
            w.getEmomInterval(),
            w.getEmomRounds(),
            w.getDescriptionEn(),
            w.getDescriptionFr(),
            w.getNotesEn(),
            w.getNotesFr());

        List<WodMovement> movements = new ArrayList<>(w.getMovements());
        movements.sort(Comparator.comparing(WodMovement::getOrderIndex));
        for (WodMovement wm : movements) {
          movementPrinter.printRecord(
              w.getTitle(),
              wm.getOrderIndex(),
              wm.getMovement().getName(),
              wm.getRepsScheme(),
              wm.getWeight(),
              name(wm.getWeightUnit()),
              wm.getDistance(),
              name(wm.getDistanceUnit()),
              wm.getDurationSeconds(),
              wm.getCalories(),
              wm.getNotesEn(),
              wm.getScalingOptionsEn(),
              name(wm.getDurationDisplayUnit()),
              join(wm.getEquipment()),
              join(wm.getTechniques()),
              wm.getWeightFemale(),
              wm.getNotesFr(),
              wm.getScalingOptionsFr(),
              wm.getCaloriesFemale(),
              wm.getHeight(),
              wm.getHeightFemale(),
              name(wm.getHeightUnit()));
        }
      }
    }
  }

  /**
   * Indexes catalog movements by exact name.
   *
   * @param movements All catalog movements.
   * @return The movements keyed by name.
   */
  public static Map<String, Movement> byName(Collection<Movement> movements) {
    return movements.stream()
        .collect(Collectors.toMap(Movement::getName, Function.identity(), (a, b) -> a));
  }

  // --- Helpers ---

  private static Iterable<CSVRecord> parse(Reader reader) throws IOException {
    return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader);
  }

  private static CSVPrinter printer(Writer writer, String[] headers) throws IOException {
    return new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(headers).get());
  }

  private static String required(CSVRecord row, String header) {
    String value = text(row, header);
    if (value == null) {
      throw new IllegalArgumentException(
          "Missing '" + header + "' at line " + row.getRecordNumber());
    }
    return value.trim();
  }

  /** Free text is kept as is (no trim) so that the round-trip stays lossless. */
  private static String text(CSVRecord row, String header) {
    if (!row.isMapped(header) || !row.isSet(header)) {
      return null;
    }
    String value = row.get(header);
    return value == null || value.isBlank() ? null : value;
  }

  /** Text of the first column present, so files written before a column rename still load. */
  private static String text(CSVRecord row, String header, String legacyHeader) {
    return row.isMapped(header) ? text(row, header) : text(row, legacyHeader);
  }

  private static Integer integer(CSVRecord row, String header) {
    String value = text(row, header);
    return value == null ? null : Integer.valueOf(value.trim());
  }

  private static Double decimal(CSVRecord row, String header) {
    String value = text(row, header);
    return value == null ? null : Double.valueOf(value.trim());
  }

  private static WodCategory category(CSVRecord row) {
    String value = text(row, "category");
    return value == null ? null : WodCategory.valueOf(value.trim().toUpperCase());
  }

  private static Unit unit(CSVRecord row, String header) {
    String value = text(row, header);
    return value == null ? null : Unit.valueOf(value.trim().toUpperCase());
  }

  private static Set<String> list(CSVRecord row, String header) {
    String value = text(row, header);
    if (value == null) {
      return new HashSet<>();
    }
    return Arrays.stream(value.split("\\" + LIST_SEPARATOR))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(HashSet::new));
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private static String join(Set<String> values) {
    return values == null || values.isEmpty()
        ? null
        : values.stream().sorted().collect(Collectors.joining(LIST_SEPARATOR));
  }
}
