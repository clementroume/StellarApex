package apex.stellar.aldebaran.dto;

/**
 * A line's load and height in both systems (D149): the prescribed value in its own unit, and in
 * the other unit the value boxes use (exact conversion rounded to the unit for competitions).
 * Sirius shows the pair of the user's system and never converts a prescription itself.
 *
 * @param weightKg Men's load in kg.
 * @param weightFemaleKg Women's load in kg.
 * @param weightLb Men's load in lb.
 * @param weightFemaleLb Women's load in lb.
 * @param heightCm Men's height in cm.
 * @param heightFemaleCm Women's height in cm.
 * @param heightIn Men's height in inches.
 * @param heightFemaleIn Women's height in inches.
 */
public record LineEquivalents(
    Double weightKg,
    Double weightFemaleKg,
    Double weightLb,
    Double weightFemaleLb,
    Double heightCm,
    Double heightFemaleCm,
    Double heightIn,
    Double heightFemaleIn) {}
