package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.ComponentTechnique;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodScaledLine;
import apex.stellar.aldebaran.service.CatalogRules;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
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
import java.util.Objects;
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
 * lossless. WOD modalities are not stored: they are recomputed from the lines (D127).
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
    "notes_fr",
    // Optional trailing columns (official tiebreak, reps of a fixed AMRAP round)
    "tiebreak_en",
    "tiebreak_fr",
    "reps_per_round",
    // Structure of the quick editor (rounds, rest, intervals, ascending ladder, weight vest)
    "rounds",
    "rest_seconds",
    "work_seconds",
    "ladder_start",
    "ladder_step",
    "vest_weight",
    "vest_weight_female",
    "vest_weight_unit",
    // Sub-category of the format (formats v3); the former wod_type values still read
    "structure",
    // Team WOD: athletes per team and how they share the work
    "team_size",
    "team_mode",
    // N of an "N RM" test (For Load, one movement)
    "rep_max"
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
    // Optional trailing columns, only needed to keep values edited in Sirius
    "duration_display_unit",
    "equipment",
    "techniques",
    "weight_female",
    "notes_fr",
    "calories_female",
    "height",
    "height_female",
    "height_unit",
    // Sets of strength work, part of the WOD (BUY_IN / MAIN / BUY_OUT, blank = MAIN)
    "sets",
    "segment",
    "complex_group",
    // Team WOD line: TOTAL (blank), EACH or TOGETHER
    "team_split",
    // Load as a percentage of the athlete's RM (base version), and the N of that RM
    "percent_of_rm",
    "rm_reps",
    // Techniques of one component of a chained line: 1:POWER|2:SPLIT
    "component_techniques",
    // Length of the course (shuttle run: between the two lines)
    "length",
    "length_unit",
    // The line in the official Scaled version (blank movement = kept RX), values of origin
    "scaled_movement",
    "scaled_equipment",
    "scaled_techniques",
    "scaled_reps_scheme",
    "scaled_weight",
    "scaled_weight_female",
    "scaled_weight_unit",
    "scaled_distance",
    "scaled_distance_unit",
    "scaled_calories",
    "scaled_calories_female",
    "scaled_height",
    "scaled_height_female",
    "scaled_height_unit",
    "scaled_duration_seconds"
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
      Format format = format(row);
      Wod wod =
          Wod.builder()
              .title(title)
              .wodType(format.type())
              .structure(format.structure())
              .teamSize(integer(row, "team_size"))
              .teamMode(enumValue(row, "team_mode", Wod.TeamMode.class))
              .repMax(integer(row, "rep_max"))
              .category(category(row))
              .scoreType(scoreType(row))
              .repScheme(text(row, "rep_scheme"))
              .timeCapSeconds(integer(row, "time_cap_seconds"))
              .emomInterval(integer(row, "emom_interval"))
              .emomRounds(integer(row, "emom_rounds"))
              .descriptionEn(text(row, "description_en", "description"))
              .descriptionFr(text(row, "description_fr"))
              .notesEn(text(row, "notes_en", "notes"))
              .notesFr(text(row, "notes_fr"))
              .tiebreakEn(text(row, "tiebreak_en"))
              .tiebreakFr(text(row, "tiebreak_fr"))
              .repsPerRound(integer(row, "reps_per_round"))
              .rounds(integer(row, "rounds"))
              .restSeconds(integer(row, "rest_seconds"))
              .workSeconds(integer(row, "work_seconds"))
              .ladderStart(integer(row, "ladder_start"))
              .ladderStep(integer(row, "ladder_step"))
              .vestWeight(decimal(row, "vest_weight"))
              .vestWeightFemale(decimal(row, "vest_weight_female"))
              .vestWeightUnit(unit(row, "vest_weight_unit"))
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
              // Former scaling options join the notes (D154)
              .notesEn(
                  withScaling(
                      text(row, "notes_en", "notes"),
                      text(row, "scaling_options_en", "scaling_options"),
                      "Scaling: "))
              .notesFr(withScaling(text(row, "notes_fr"), text(row, "scaling_options_fr"), "Scaling : "))
              .caloriesFemale(integer(row, "calories_female"))
              .height(decimal(row, "height"))
              .heightFemale(decimal(row, "height_female"))
              .heightUnit(unit(row, "height_unit"))
              .equipment(list(row, "equipment"))
              .techniques(list(row, "techniques"))
              .sets(integer(row, "sets"))
              .segment(segment(row))
              .complexGroup(integer(row, "complex_group"))
              .teamSplit(
                  Objects.requireNonNullElse(
                      enumValue(row, "team_split", WodMovement.TeamSplit.class),
                      WodMovement.TeamSplit.TOTAL))
              .percentOfRm(decimal(row, "percent_of_rm"))
              .rmReps(integer(row, "rm_reps"))
              .componentTechniques(componentTechniques(row))
              .length(decimal(row, "length"))
              .lengthUnit(unit(row, "length_unit"))
              .build();
      wodMovement.checkVariantsAllowed(
          movement.getName(), movement.getEquipment(), movement.getTechniques());
      wodMovement.setScaled(scaledLine(row, movementsByName, wodTitle));
      wod.getMovements().add(wodMovement);
    }

    wodsByTitle
        .values()
        .forEach(w -> w.getMovements().sort(Comparator.comparing(WodMovement::getOrderIndex)));
    // One modality per line: family, usual name, load (D127)
    wodsByTitle.values().forEach(w -> w.getModalities().addAll(CatalogRules.modalities(w.getMovements())));
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
            w.getNotesFr(),
            w.getTiebreakEn(),
            w.getTiebreakFr(),
            w.getRepsPerRound(),
            w.getRounds(),
            w.getRestSeconds(),
            w.getWorkSeconds(),
            w.getLadderStart(),
            w.getLadderStep(),
            w.getVestWeight(),
            w.getVestWeightFemale(),
            name(w.getVestWeightUnit()),
            name(w.getStructure()),
            w.getTeamSize(),
            name(w.getTeamMode()),
            w.getRepMax());

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
              name(wm.getDurationDisplayUnit()),
              join(wm.getEquipment()),
              join(wm.getTechniques()),
              wm.getWeightFemale(),
              wm.getNotesFr(),
              wm.getCaloriesFemale(),
              wm.getHeight(),
              wm.getHeightFemale(),
              name(wm.getHeightUnit()),
              wm.getSets(),
              wm.getSegment() == Segment.MAIN ? null : wm.getSegment().name(),
              wm.getComplexGroup(),
              wm.getTeamSplit() == WodMovement.TeamSplit.TOTAL ? null : wm.getTeamSplit().name(),
              wm.getPercentOfRm(),
              wm.getRmReps(),
              joinComponents(wm.getComponentTechniques()),
              wm.getLength(),
              name(wm.getLengthUnit()),
              scaledColumn(wm, sl -> sl.getMovement().getName()),
              scaledColumn(wm, sl -> join(sl.getEquipment())),
              scaledColumn(wm, sl -> join(sl.getTechniques())),
              scaledColumn(wm, WodScaledLine::getRepsScheme),
              scaledColumn(wm, WodScaledLine::getWeight),
              scaledColumn(wm, WodScaledLine::getWeightFemale),
              scaledColumn(wm, sl -> name(sl.getWeightUnit())),
              scaledColumn(wm, WodScaledLine::getDistance),
              scaledColumn(wm, sl -> name(sl.getDistanceUnit())),
              scaledColumn(wm, WodScaledLine::getCalories),
              scaledColumn(wm, WodScaledLine::getCaloriesFemale),
              scaledColumn(wm, WodScaledLine::getHeight),
              scaledColumn(wm, WodScaledLine::getHeightFemale),
              scaledColumn(wm, sl -> name(sl.getHeightUnit())),
              scaledColumn(wm, WodScaledLine::getDurationSeconds));
        }
      }
    }
  }

  /** Notes followed by the former scaling options of a line, when a file still has them. */
  private static String withScaling(String notes, String scaling, String prefix) {
    if (scaling == null) {
      return notes;
    }
    return notes == null ? prefix + scaling : notes + "\n\n" + prefix + scaling;
  }

  /** The line in the official Scaled version written in a row, or null (kept RX). */
  private static WodScaledLine scaledLine(
      CSVRecord row, Map<String, Movement> movementsByName, String wodTitle) {
    String name = text(row, "scaled_movement");
    if (name == null) {
      return null;
    }
    Movement movement = movementsByName.get(name.trim());
    if (movement == null) {
      log.warn("Unknown Scaled movement '{}' for WOD '{}', kept RX", name, wodTitle);
      return null;
    }
    return WodScaledLine.builder()
        .movement(movement)
        .equipment(list(row, "scaled_equipment"))
        .techniques(list(row, "scaled_techniques"))
        .repsScheme(text(row, "scaled_reps_scheme"))
        .weight(decimal(row, "scaled_weight"))
        .weightFemale(decimal(row, "scaled_weight_female"))
        .weightUnit(unit(row, "scaled_weight_unit"))
        .distance(decimal(row, "scaled_distance"))
        .distanceUnit(unit(row, "scaled_distance_unit"))
        .calories(integer(row, "scaled_calories"))
        .caloriesFemale(integer(row, "scaled_calories_female"))
        .height(decimal(row, "scaled_height"))
        .heightFemale(decimal(row, "scaled_height_female"))
        .heightUnit(unit(row, "scaled_height_unit"))
        .durationSeconds(integer(row, "scaled_duration_seconds"))
        .build();
  }

  private static Object scaledColumn(WodMovement wm, Function<WodScaledLine, Object> value) {
    return wm.getScaled() == null ? null : value.apply(wm.getScaled());
  }

  private static Set<ComponentTechnique> componentTechniques(CSVRecord row) {
    return list(row, "component_techniques").stream()
        .map(
            value -> {
              String[] parts = value.split(":", 2);
              return new ComponentTechnique(Integer.valueOf(parts[0]), parts[1]);
            })
        .collect(Collectors.toCollection(HashSet::new));
  }

  private static String joinComponents(Set<ComponentTechnique> values) {
    return join(
        values.stream()
            .map(v -> v.componentOrder() + ":" + v.technique())
            .collect(Collectors.toSet()));
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

  /** Score type; the former ROUNDS_REPS (AMRAPs, now stored as total reps) reads as REPS. */
  private static ScoreType scoreType(CSVRecord row) {
    String value = required(row, "score_type").toUpperCase();
    return "ROUNDS_REPS".equals(value) ? ScoreType.REPS : ScoreType.valueOf(value);
  }

  /** Format and sub-category of a WOD row. */
  private record Format(WodType type, WodStructure structure) {}

  /**
   * Format of a row: the {@code structure} column when given, else the sub-category implied by a
   * former {@code wod_type} value (CHIPPER, LADDER, INTERVALS, DEATH_BY, STRENGTH, SKILL, ACCESSORY,
   * CUSTOM), so that files written before formats v3 still load.
   */
  private static Format format(CSVRecord row) {
    String type = required(row, "wod_type").toUpperCase();
    String structureText = text(row, "structure");
    WodStructure structure =
        structureText == null ? null : WodStructure.valueOf(structureText.trim().toUpperCase());
    Format legacy =
        switch (type) {
          case "CHIPPER" -> new Format(WodType.FOR_TIME, WodStructure.CHIPPER);
          case "LADDER" ->
              text(row, "ladder_start") == null
                  ? new Format(WodType.FOR_TIME, WodStructure.SCHEME)
                  : new Format(WodType.FOR_TIME, WodStructure.LADDER);
          case "INTERVALS" -> new Format(WodType.EMOM, WodStructure.INTERVALS);
          case "DEATH_BY" -> new Format(WodType.EMOM, WodStructure.DEATH_BY);
          case "STRENGTH" -> new Format(WodType.FOR_LOAD, null);
          case "SKILL", "ACCESSORY" -> new Format(WodType.FOR_QUALITY, WodStructure.PRACTICE);
          case "CUSTOM" -> new Format(WodType.AMRAP, WodStructure.MAX_REPS);
          default -> new Format(WodType.valueOf(type), null);
        };
    return new Format(legacy.type(), structure != null ? structure : legacy.structure());
  }

  /** Part of the WOD; blank (or a file without the column) means the rounds. */
  private static Segment segment(CSVRecord row) {
    String value = text(row, "segment");
    return value == null ? Segment.MAIN : Segment.valueOf(value.trim().toUpperCase());
  }

  private static <E extends Enum<E>> E enumValue(CSVRecord row, String header, Class<E> type) {
    String value = text(row, header);
    return value == null ? null : Enum.valueOf(type, value.trim().toUpperCase());
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
    if (value == null) {
      return null;
    }
    String name = value.trim().toUpperCase();
    // The former "box benchmark" category no longer exists (former V13)
    return "CUSTOM_BENCHMARK".equals(name) ? null : WodCategory.valueOf(name);
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
