package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.ScoreTeam;
import org.springframework.data.jpa.repository.JpaRepository;

/** Teams of team WOD scores. */
public interface ScoreTeamRepository extends JpaRepository<ScoreTeam, Long> {}
