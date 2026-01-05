package apex.stellar.aldebaran.mapper;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.enums.Unit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper for converting between {@link WodScore} entities and DTOs.
 *
 * <p><b>Conversion Strategy:</b>
 *
 * <ul>
 *   <li><b>Normalization (Request -> Entity):</b> Converts user-provided units (e.g., LBS, MILES)
 *       into system canonical units (KG, METERS) using {@link Unit#toBase(double)}.
 *   <li><b>Display (Entity -> Response):</b> Converts canonical units back to the user's preferred
 *       display unit (stored in the entity) using {@link Unit#fromBase(double)}.
 *   <li><b>Time Handling:</b> Aggregates split time inputs (Minutes/Seconds) into total seconds for
 *       storage, and splits them back for display.
 * </ul>
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {WodMapper.class})
public interface WodScoreMapper {

  // -------------------------------------------------------------------------
  // Entity -> Response
  // -------------------------------------------------------------------------

  @Mapping(target = "wodSummary", source = "wod")
  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "timeMinutesPart", source = "score", qualifiedByName = "calculateMinutes")
  @Mapping(target = "timeSecondsPart", source = "score", qualifiedByName = "calculateSeconds")
  @Mapping(target = "maxWeight", source = "score", qualifiedByName = "mapMaxWeightToDisplay")
  @Mapping(target = "totalLoad", source = "score", qualifiedByName = "mapTotalLoadToDisplay")
  @Mapping(target = "totalDistance", source = "score", qualifiedByName = "mapDistanceToDisplay")
  @Mapping(target = "weightUnit", source = "weightDisplayUnit")
  @Mapping(target = "distanceUnit", source = "distanceDisplayUnit")
  WodScoreResponse toResponse(WodScore score);

  // -------------------------------------------------------------------------
  // Request -> Entity
  // -------------------------------------------------------------------------

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "wod", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "loggedAt", ignore = true)
  @Mapping(target = "personalRecord", ignore = true)
  // Smart Time Mapping
  @Mapping(target = "timeSeconds", source = "request", qualifiedByName = "calculateTotalSeconds")
  @Mapping(target = "timeDisplayUnit", source = "request", qualifiedByName = "inferTimeDisplayUnit")
  // Normalization
  @Mapping(target = "maxWeightKg", source = "request", qualifiedByName = "normalizeMaxWeight")
  @Mapping(target = "totalLoadKg", source = "request", qualifiedByName = "normalizeTotalLoad")
  @Mapping(
      target = "totalDistanceMeters",
      source = "request",
      qualifiedByName = "normalizeDistance")
  @Mapping(target = "weightDisplayUnit", source = "weightUnit")
  @Mapping(target = "distanceDisplayUnit", source = "distanceUnit")
  WodScore toEntity(WodScoreRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "wod", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "loggedAt", ignore = true)
  @Mapping(target = "personalRecord", ignore = true)
  @Mapping(target = "timeSeconds", source = "request", qualifiedByName = "calculateTotalSeconds")
  @Mapping(target = "timeDisplayUnit", source = "request", qualifiedByName = "inferTimeDisplayUnit")
  @Mapping(target = "maxWeightKg", source = "request", qualifiedByName = "normalizeMaxWeight")
  @Mapping(target = "totalLoadKg", source = "request", qualifiedByName = "normalizeTotalLoad")
  @Mapping(
      target = "totalDistanceMeters",
      source = "request",
      qualifiedByName = "normalizeDistance")
  @Mapping(target = "weightDisplayUnit", source = "weightUnit")
  @Mapping(target = "distanceDisplayUnit", source = "distanceUnit")
  void updateEntity(WodScoreRequest request, @MappingTarget WodScore entity);

  // -------------------------------------------------------------------------
  // Time Logic Helpers
  // -------------------------------------------------------------------------

  /**
   * Calculates the total duration in seconds from separate minutes and seconds inputs.
   *
   * @param req The score request containing time fields.
   * @return The total time in seconds, or null if no time is provided.
   */
  @Named("calculateTotalSeconds")
  default Integer calculateTotalSeconds(WodScoreRequest req) {
    if (req.timeMinutes() == null && req.timeSeconds() == null) {
      return null;
    }
    int mins = req.timeMinutes() != null ? req.timeMinutes() : 0;
    int secs = req.timeSeconds() != null ? req.timeSeconds() : 0;
    return (mins * 60) + secs;
  }

  /**
   * Infers the preferred time display unit based on the provided input fields.
   *
   * @param req The score request.
   * @return {@link Unit#MINUTES} if minutes were explicitly provided, otherwise {@link Unit#SECONDS}.
   */
  @Named("inferTimeDisplayUnit")
  default Unit inferTimeDisplayUnit(WodScoreRequest req) {
    // If user explicitly provided minutes, we assume they prefer "Minutes" display
    if (req.timeMinutes() != null) {
      return Unit.MINUTES;
    }
    // Default to SECONDS if only seconds were provided (e.g. "90")
    return Unit.SECONDS;
  }

  /**
   * Extracts the whole minutes part from the total seconds.
   *
   * @param s The WOD score entity.
   * @return The number of full minutes, or null if time is not set.
   */
  @Named("calculateMinutes")
  default Integer calculateMinutes(WodScore s) {
    return s.getTimeSeconds() != null ? s.getTimeSeconds() / 60 : null;
  }

  /**
   * Extracts the remaining seconds part from the total seconds.
   *
   * @param s The WOD score entity.
   * @return The remaining seconds (0-59), or null if time is not set.
   */
  @Named("calculateSeconds")
  default Integer calculateSeconds(WodScore s) {
    return s.getTimeSeconds() != null ? s.getTimeSeconds() % 60 : null;
  }

  // -------------------------------------------------------------------------
  // Normalization Helpers
  // -------------------------------------------------------------------------

  /**
   * Converts the maximum weight to the system base unit (Kilograms).
   *
   * @param req The score request.
   * @return The weight in KG, or null if not provided.
   */
  @Named("normalizeMaxWeight")
  default Double normalizeMaxWeight(WodScoreRequest req) {
    if (req.maxWeight() == null || req.weightUnit() == null) {
      return null;
    }
    return req.weightUnit().toBase(req.maxWeight());
  }

  /**
   * Converts the total load to the system base unit (Kilograms).
   *
   * @param req The score request.
   * @return The total load in KG, or null if not provided.
   */
  @Named("normalizeTotalLoad")
  default Double normalizeTotalLoad(WodScoreRequest req) {
    if (req.totalLoad() == null || req.weightUnit() == null) {
      return null;
    }
    return req.weightUnit().toBase(req.totalLoad());
  }

  /**
   * Converts the total distance to the system base unit (Meters).
   *
   * @param req The score request.
   * @return The distance in Meters, or null if not provided.
   */
  @Named("normalizeDistance")
  default Double normalizeDistance(WodScoreRequest req) {
    if (req.totalDistance() == null || req.distanceUnit() == null) {
      return null;
    }
    return req.distanceUnit().toBase(req.totalDistance());
  }

  // -------------------------------------------------------------------------
  // Display Helpers
  // -------------------------------------------------------------------------

  /**
   * Converts the maximum weight from the base unit to the user's preferred display unit.
   *
   * @param s The WOD score entity.
   * @return The weight in the user's preferred unit.
   */
  @Named("mapMaxWeightToDisplay")
  default Double mapMaxWeightToDisplay(WodScore s) {
    if (s.getMaxWeightKg() == null || s.getWeightDisplayUnit() == null) {
      return null;
    }
    return s.getWeightDisplayUnit().fromBase(s.getMaxWeightKg());
  }

  /**
   * Converts the total load from the base unit to the user's preferred display unit.
   *
   * @param s The WOD score entity.
   * @return The total load in the user's preferred unit.
   */
  @Named("mapTotalLoadToDisplay")
  default Double mapTotalLoadToDisplay(WodScore s) {
    if (s.getTotalLoadKg() == null || s.getWeightDisplayUnit() == null) {
      return null;
    }
    return s.getWeightDisplayUnit().fromBase(s.getTotalLoadKg());
  }

  /**
   * Converts the total distance from the base unit to the user's preferred display unit.
   *
   * @param s The WOD score entity.
   * @return The distance in the user's preferred unit.
   */
  @Named("mapDistanceToDisplay")
  default Double mapDistanceToDisplay(WodScore s) {
    if (s.getTotalDistanceMeters() == null || s.getDistanceDisplayUnit() == null) {
      return null;
    }
    return s.getDistanceDisplayUnit().fromBase(s.getTotalDistanceMeters());
  }
}
