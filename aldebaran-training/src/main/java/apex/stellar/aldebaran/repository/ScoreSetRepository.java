package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.ScoreSet;
import org.springframework.data.jpa.repository.JpaRepository;

/** Strength sets of scores, each on its line of the WOD (written through {@code Score.sets}). */
public interface ScoreSetRepository extends JpaRepository<ScoreSet, Long> {}
