package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import java.time.LocalDate;

/** DTO representing a logged score. */
public record ScoreResponse(

    // --- Identification ---
    Long id,
    Long userId,
    LocalDate date,
    WodSummaryResponse wodSummary,

    // --- Time Metrics ---
    Integer timeSeconds,
    Integer timeMinutesPart,
    Integer timeSecondsPart,
    Unit timeDisplayUnit,

    // --- Volume Metrics ---
    Integer rounds,
    Integer reps,

    // --- Load Metrics ---
    Double maxWeight,
    Double totalLoad,
    Unit weightUnit,

    // --- Distance Metrics ---
    Double totalDistance,
    Unit distanceUnit,

    // --- Calories ---
    Integer totalCalories,

    // --- Performance Context ---
    ScalingLevel scaling,
    boolean timeCapped,
    boolean personalRecord,

    // --- Comments and Scaling notes---
    String userComment,
    String scalingNotes,

    // --- Audit ---
    LocalDate loggedAt) {}
