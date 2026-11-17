package apex.stellar.aldebaran.dto;

import java.util.List;

/** DTO containing structural reference data for WOD Score forms. */
public record ScoreReferenceData(
    // --- Scaling Level ---
    List<String> scalingLevels,
    // --- Cardio equivalences proposed for a substitution (D151) ---
    List<CardioEquivalent> cardio) {}
