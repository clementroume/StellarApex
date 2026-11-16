package apex.stellar.aldebaran.dto;

import java.util.Set;

/**
 * A line of the WOD done otherwise than prescribed (D142), as stored: null values are as
 * prescribed. Load in the score's weight unit, distance and height in meters.
 *
 * @param wodMovementId The line of the WOD.
 * @param movement The movement done.
 * @param display How the line done reads (usual name of the variant done).
 */
public record ScoreLineResponse(
    Long wodMovementId,
    MovementSummaryResponse movement,
    Set<String> equipment,
    Set<String> techniques,
    String repsScheme,
    Double weight,
    Double distanceMeters,
    Integer calories,
    Double heightMeters,
    Integer durationSeconds,
    WodMovementDisplay display) {}
