package apex.stellar.aldebaran.dto;

/**
 * An estimated N RM, displayed where the athlete has no record (D63): never a record.
 *
 * @param reps N of the N RM.
 * @param weightKg The estimated load, in kg.
 */
public record RecordEstimateResponse(int reps, double weightKg) {}
