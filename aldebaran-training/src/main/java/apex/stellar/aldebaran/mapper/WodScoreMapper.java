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

  @Named("calculateTotalSeconds")
  default Integer calculateTotalSeconds(WodScoreRequest req) {
    if (req.timeMinutes() == null && req.timeSeconds() == null) return null;
    int mins = req.timeMinutes() != null ? req.timeMinutes() : 0;
    int secs = req.timeSeconds() != null ? req.timeSeconds() : 0;
    return (mins * 60) + secs;
  }

  @Named("inferTimeDisplayUnit")
  default Unit inferTimeDisplayUnit(WodScoreRequest req) {
    // If user explicitly provided minutes, we assume they prefer "Minutes" display
    if (req.timeMinutes() != null) {
      return Unit.MINUTES;
    }
    // Default to SECONDS if only seconds were provided (e.g. "90")
    return Unit.SECONDS;
  }

  @Named("calculateMinutes")
  default Integer calculateMinutes(WodScore s) {
    return s.getTimeSeconds() != null ? s.getTimeSeconds() / 60 : null;
  }

  @Named("calculateSeconds")
  default Integer calculateSeconds(WodScore s) {
    return s.getTimeSeconds() != null ? s.getTimeSeconds() % 60 : null;
  }

  // -------------------------------------------------------------------------
  // Normalization Helpers
  // -------------------------------------------------------------------------

  @Named("normalizeMaxWeight")
  default Double normalizeMaxWeight(WodScoreRequest req) {
    if (req.maxWeight() == null || req.weightUnit() == null) return null;
    return req.weightUnit().toBase(req.maxWeight());
  }

  @Named("normalizeTotalLoad")
  default Double normalizeTotalLoad(WodScoreRequest req) {
    if (req.totalLoad() == null || req.weightUnit() == null) return null;
    return req.weightUnit().toBase(req.totalLoad());
  }

  @Named("normalizeDistance")
  default Double normalizeDistance(WodScoreRequest req) {
    if (req.totalDistance() == null || req.distanceUnit() == null) return null;
    return req.distanceUnit().toBase(req.totalDistance());
  }

  // -------------------------------------------------------------------------
  // Display Helpers
  // -------------------------------------------------------------------------

  @Named("mapMaxWeightToDisplay")
  default Double mapMaxWeightToDisplay(WodScore s) {
    if (s.getMaxWeightKg() == null || s.getWeightDisplayUnit() == null) return null;
    return s.getWeightDisplayUnit().fromBase(s.getMaxWeightKg());
  }

  @Named("mapTotalLoadToDisplay")
  default Double mapTotalLoadToDisplay(WodScore s) {
    if (s.getTotalLoadKg() == null || s.getWeightDisplayUnit() == null) return null;
    return s.getWeightDisplayUnit().fromBase(s.getTotalLoadKg());
  }

  @Named("mapDistanceToDisplay")
  default Double mapDistanceToDisplay(WodScore s) {
    if (s.getTotalDistanceMeters() == null || s.getDistanceDisplayUnit() == null) return null;
    return s.getDistanceDisplayUnit().fromBase(s.getTotalDistanceMeters());
  }
}
