package apex.stellar.aldebaran.dto;

/**
 * What one meter of running is worth on a cardio movement (D151), to propose the quantity of a
 * substitution (Run 400 m done on the rower: 500 m or 40 cal). Box tables, rough on purpose.
 *
 * @param movementName The catalog movement (Running, Rowing, Ski Erg, Biking).
 * @param usualNameEn A usual name of it with its own values (Echo Bike), or null for the movement.
 * @param meters Meters per meter of running, or null.
 * @param calories Men's calories per meter of running, or null.
 * @param caloriesFemale Women's calories per meter of running, or null (same as men).
 */
public record CardioEquivalent(
    String movementName,
    String usualNameEn,
    Double meters,
    Double calories,
    Double caloriesFemale) {}
