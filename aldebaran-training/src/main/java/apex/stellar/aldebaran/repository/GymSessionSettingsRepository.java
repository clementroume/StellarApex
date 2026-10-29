package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.GymSessionSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository of the gyms' session settings. */
@Repository
public interface GymSessionSettingsRepository extends JpaRepository<GymSessionSettings, Long> {}
