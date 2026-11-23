package apex.stellar.aldebaran.model.entities;

import java.util.Locale;

/**
 * The stable code of a catalog entry (muscle, movement, catalog WOD): derived once from its name,
 * never changed afterwards, so renaming keeps every link (3D model, catalog migrations).
 */
public final class CatalogCode {

  private CatalogCode() {}

  /**
   * The code a name gives: lower case, every run of other characters replaced by one dash (same
   * rule as the migrations and the muscle model pipeline).
   *
   * @param name The name.
   * @param fallback The code when the name holds no latin letter or digit.
   * @return The code.
   */
  public static String of(String name, String fallback) {
    String code =
        name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return code.isEmpty() ? fallback : code;
  }
}
