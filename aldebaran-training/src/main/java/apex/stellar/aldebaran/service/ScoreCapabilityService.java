package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ScoreCapabilities;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.ScoreSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Adapts score responses to the requesting user: attaches their {@link ScoreCapabilities} and hides
 * other athletes' comments and scaling notes from users who may not moderate the score.
 */
@Service
@RequiredArgsConstructor
public class ScoreCapabilityService {

  private final ScoreSecurity scoreSecurity;

  /**
   * Returns a copy of the score adapted to the given viewer.
   *
   * @param score The score.
   * @param principal The authenticated user.
   * @return The score with the viewer's capabilities, private notes hidden when needed.
   */
  public ScoreResponse forViewer(ScoreResponse score, AldebaranUserPrincipal principal) {
    boolean canModify = scoreSecurity.canModify(score, principal);
    // The athlete and the staff able to moderate the score see its notes
    return score.forViewer(new ScoreCapabilities(canModify), canModify);
  }
}
