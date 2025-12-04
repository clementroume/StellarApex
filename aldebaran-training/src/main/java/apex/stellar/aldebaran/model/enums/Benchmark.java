package apex.stellar.aldebaran.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumeration of standard CrossFit benchmark workouts.
 *
 * <p>This enum catalogs the widely recognized "Girls" and "Hero" WODs (Workout of the Day) used as
 * reference points to measure athletic performance and progress over time. Each constant represents
 * a specific, standardized workout definition.
 */
@Getter
@RequiredArgsConstructor
public enum Benchmark {

  // --- The Girls ---
  AMANDA("Amanda", BenchmarkCategory.GIRL),
  ANGIE("Angie", BenchmarkCategory.GIRL),
  ANNIE("Annie", BenchmarkCategory.GIRL),
  BARBARA("Barbara", BenchmarkCategory.GIRL),
  CANDY("Candy", BenchmarkCategory.GIRL),
  CHELSEA("Chelsea", BenchmarkCategory.GIRL),
  CINDY("Cindy", BenchmarkCategory.GIRL),
  DIANE("Diane", BenchmarkCategory.GIRL),
  ELIZABETH("Elizabeth", BenchmarkCategory.GIRL),
  EVA("Eva", BenchmarkCategory.GIRL),
  FRAN("Fran", BenchmarkCategory.GIRL),
  GRACE("Grace", BenchmarkCategory.GIRL),
  GWEN("Gwen", BenchmarkCategory.GIRL),
  HELEN("Helen", BenchmarkCategory.GIRL),
  HOPE("Hope", BenchmarkCategory.GIRL),
  ISABEL("Isabel", BenchmarkCategory.GIRL),
  JACKIE("Jackie", BenchmarkCategory.GIRL),
  KAREN("Karen", BenchmarkCategory.GIRL),
  KELLY("Kelly", BenchmarkCategory.GIRL),
  LINDA("Linda", BenchmarkCategory.GIRL),
  LYNNE("Lynne", BenchmarkCategory.GIRL),
  MAGGIE("Maggie", BenchmarkCategory.GIRL),
  MARGUERITA("Marguerita", BenchmarkCategory.GIRL),
  MARY("Mary", BenchmarkCategory.GIRL),
  NANCY("Nancy", BenchmarkCategory.GIRL),
  NICOLE("Nicole", BenchmarkCategory.GIRL),

  // --- Hero WODs ---
  MURPH("Murph", BenchmarkCategory.HERO),
  CHAD("Chad", BenchmarkCategory.HERO),
  DT("DT", BenchmarkCategory.HERO),
  GRIFF("Griff", BenchmarkCategory.HERO),
  JT("JT", BenchmarkCategory.HERO),
  MICHAEL("Michael", BenchmarkCategory.HERO),
  NATE("Nate", BenchmarkCategory.HERO),
  RANDY("Randy", BenchmarkCategory.HERO),
  RYAN("Ryan", BenchmarkCategory.HERO),
  THE_SEVEN("The Seven", BenchmarkCategory.HERO),
  TOMMY("Tommy", BenchmarkCategory.HERO),
  BADGER("Badger", BenchmarkCategory.HERO),
  DANIEL("Daniel", BenchmarkCategory.HERO),
  JOSH("Josh", BenchmarkCategory.HERO),

  // --- Other ---
  /**
   * Represents a custom benchmark created by a user or a specific box, distinct from the
   * standardized lists.
   */
  CUSTOM("Custom", BenchmarkCategory.OTHER);

  /** A human-readable name suitable for UI display. */
  private final String displayName;

  /** The category of the benchmark (e.g., GIRL, HERO). */
  private final BenchmarkCategory category;

  /** Categorization of benchmark workouts. */
  public enum BenchmarkCategory {
    /** The classic "Girls" benchmarks (e.g., Fran, Cindy). */
    GIRL,
    /** Benchmarks named after fallen service members. */
    HERO,
    /** Non-standard or custom benchmarks. */
    OTHER
  }
}
