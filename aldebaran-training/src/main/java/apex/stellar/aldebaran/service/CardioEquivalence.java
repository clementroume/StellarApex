package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.CardioEquivalent;
import java.util.List;

/**
 * Box conversion tables between cardio machines (D151, tables of CrossFit Pasig /
 * @theboxprogramming and Daybreak, concordant): 200 m of running = 250 m or 20 cal of rowing or
 * skiing = 500 m or 20 cal on a BikeErg = 15 cal (men) / 10 cal (women) on an air bike. Used to
 * propose the quantity of a substitution; the athlete corrects it.
 */
public final class CardioEquivalence {

  private CardioEquivalence() {}

  /** Per meter of running. */
  public static final List<CardioEquivalent> TABLE =
      List.of(
          new CardioEquivalent("Running", null, 1.0, null, null),
          new CardioEquivalent("Rowing", null, 1.25, 0.1, null),
          new CardioEquivalent("Ski Erg", null, 1.25, 0.1, null),
          new CardioEquivalent("Biking", null, 2.5, 0.1, null),
          new CardioEquivalent("Biking", "Echo Bike", 3.125, 0.075, 0.05),
          new CardioEquivalent("Biking", "Assault Bike", 3.125, 0.075, 0.05));
}
