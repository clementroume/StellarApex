package apex.stellar.antares.repository.jpa;

import apex.stellar.antares.model.BodyWeight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Body weight history of users. */
@Repository
public interface BodyWeightRepository extends JpaRepository<@NonNull BodyWeight, @NonNull Long> {

  /**
   * The user's measures, most recent first.
   *
   * @param userId The user.
   * @param pageable The page (size caps the history returned).
   * @return The measures.
   */
  List<BodyWeight> findByUserIdOrderByMeasuredOnDesc(Long userId, Pageable pageable);

  /**
   * The user's measure of a day, if any.
   *
   * @param userId The user.
   * @param measuredOn The day.
   * @return The measure.
   */
  Optional<BodyWeight> findByUserIdAndMeasuredOn(Long userId, LocalDate measuredOn);

  /**
   * One measure of a user (never another user's).
   *
   * @param id The measure.
   * @param userId The owner.
   * @return The measure.
   */
  Optional<BodyWeight> findByIdAndUserId(Long id, Long userId);
}
