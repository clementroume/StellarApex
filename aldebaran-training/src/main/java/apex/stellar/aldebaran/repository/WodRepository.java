package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing {@link Wod} entities.
 *
 * <p>Provides access to workout definitions ("The Recipe").
 */
@Repository
public interface WodRepository extends JpaRepository<Wod, Long> {

  /**
   * Retrieves all WODs matching a specific structural type. Useful for filtering benchmarks (e.g.,
   * finding all "GIRLS" or "HERO" WODs).
   *
   * @param wodType The {@link WodType} to filter by.
   * @return A list of WODs matching the type.
   */
  List<Wod> findByWodType(WodType wodType);

  /**
   * Searches for WODs by their title, ignoring case sensitivity.
   *
   * @param title The partial title to search for (e.g., "Murph").
   * @return A list of matching WODs.
   */
  List<Wod> findByTitleContainingIgnoreCase(String title);
}
