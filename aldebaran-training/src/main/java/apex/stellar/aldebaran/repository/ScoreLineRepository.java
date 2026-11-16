package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.ScoreLine;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lines of scores done otherwise than prescribed (written through {@code Score.lines}). */
public interface ScoreLineRepository extends JpaRepository<ScoreLine, Long> {}
