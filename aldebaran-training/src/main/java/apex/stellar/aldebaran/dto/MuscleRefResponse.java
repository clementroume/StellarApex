package apex.stellar.aldebaran.dto;

/** Lightweight reference to a muscle, used for functional group links (parent and members). */
public record MuscleRefResponse(
    Long id, String medicalName, String commonNameEn, String commonNameFr) {}
