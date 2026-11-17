package apex.stellar.aldebaran.dto;

import java.util.List;

/**
 * The real sizes of the weighted implements, in both systems (D155): what a box owns, so the only
 * loads the forms offer and the ones a conversion lands on.
 *
 * @param kettlebellKg Kettlebells in kg.
 * @param kettlebellLb Kettlebells in lb.
 * @param dumbbellKg Dumbbells in kg.
 * @param dumbbellLb Dumbbells in lb.
 * @param medballKg Medballs in kg.
 * @param medballLb Medballs in lb.
 * @param boxCm Plyo boxes in cm.
 * @param boxIn Plyo boxes in inches.
 */
public record ImplementSizes(
    List<Double> kettlebellKg,
    List<Double> kettlebellLb,
    List<Double> dumbbellKg,
    List<Double> dumbbellLb,
    List<Double> medballKg,
    List<Double> medballLb,
    List<Double> boxCm,
    List<Double> boxIn) {}
