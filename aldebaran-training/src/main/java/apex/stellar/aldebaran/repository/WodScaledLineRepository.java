package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.WodScaledLine;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lines of the official Scaled versions of WODs (written through {@code WodMovement.scaled}). */
public interface WodScaledLineRepository extends JpaRepository<WodScaledLine, Long> {}
