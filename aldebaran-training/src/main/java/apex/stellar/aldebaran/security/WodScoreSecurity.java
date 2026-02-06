package apex.stellar.aldebaran.security;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security bean guarding WOD Scores (Participation & Moderation).
 *
 * <p>This component enforces authorization rules for logging and managing scores:
 *
 * <ul>
 *   <li><b>Participation (Self):</b> Athletes can log scores for Public WODs, their Gym's WODs, or
 *       their Private WODs.
 *   <li><b>Moderation (Staff):</b> Coaches/Owners can log or correct scores for other athletes
 *       <i>within their Gym context</i>.
 * </ul>
 *
 * <p><b>Strategy regarding "Not Found":</b> Methods return {@code true} if the resource ID is not
 * found. This delegates the responsibility to the Service layer to throw a proper {@code
 * ResourceNotFoundException} (HTTP 404), rather than triggering a generic {@code
 * AccessDeniedException} (HTTP 403).
 */
@Component("wodScoreSecurity")
@RequiredArgsConstructor
public class WodScoreSecurity {

  private final WodScoreRepository scoreRepository;
  private final WodRepository wodRepository;

  /**
   * Evaluates if the authenticated user is authorized to view a specific score.
   *
   * @param scoreId The unique identifier of the score.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the score is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canView(Long scoreId, AldebaranUserPrincipal principal) {

    if (SecurityUtils.isAdmin(principal)) {
      return true;
    }

    return scoreRepository
        .findById(scoreId)
        .map(
            score -> {
              // 1. My Score -> Always visible
              if (Objects.equals(score.getUserId(), principal.getId())) {
                return true;
              }

              Wod wod = score.getWod();

              // 2. Public WOD -> Visible to everyone (Global Leaderboard)
              if (wod.isPublic()) {
                return true;
              }

              // 3. Gym WOD -> Visible to all members of that gym (Gym Leaderboard)
              if (wod.getGymId() != null) {
                return Objects.equals(wod.getGymId(), principal.getGymId());
              }

              // 4. Private WOD -> Visible only to the owner (already covered by step 1)
              return false;
            })
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user is authorized to create (log) a score. Handles both
   * Self-Participation and Staff Moderation.
   *
   * @param request The score creation payload.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the WOD is not found (deferring to 404).
   */
  public boolean canCreate(WodScoreRequest request, AldebaranUserPrincipal principal) {

    if (SecurityUtils.isAdmin(principal)) {
      return true;
    }

    // Determine intent: Self-logging vs Logging for others
    Long targetUserId = request.userId() != null ? request.userId() : principal.getId();
    boolean isSelf = Objects.equals(targetUserId, principal.getId());

    return wodRepository
        .findById(request.wodId())
        .map(
            wod -> {
              // CASE A: Self-Participation
              if (isSelf) {
                // 1. Public: Open to all
                if (wod.isPublic()) {
                  return true;
                }
                // 2. Gym: Must be a member
                if (wod.getGymId() != null) {
                  return Objects.equals(wod.getGymId(), principal.getGymId());
                }
                // 3. Private: Author only
                return Objects.equals(wod.getAuthorId(), principal.getId());
              }

              // CASE B: Staff Moderation (Logging for someone else)
              // Requires: Gym WOD + Same Gym Context + Verification Rights
              return wod.getGymId() != null
                  && Objects.equals(wod.getGymId(), principal.getGymId())
                  && SecurityUtils.hasScoreVerificationRights(principal);
            })
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user is authorized to update OR delete a score.
   *
   * @param scoreId The unique identifier of the score.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the score is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canModify(Long scoreId, AldebaranUserPrincipal principal) {

    if (SecurityUtils.isAdmin(principal)) {
      return true;
    }

    return scoreRepository
        .findById(scoreId)
        .map(
            score -> {
              // 1. Self-Correction: Users can always modify their own scores
              if (Objects.equals(score.getUserId(), principal.getId())) {
                return true;
              }

              // 2. Staff Moderation: Must be Gym WOD + Same Gym + Rights
              Wod wod = score.getWod();
              return wod.getGymId() != null
                  && Objects.equals(wod.getGymId(), principal.getGymId())
                  && SecurityUtils.hasScoreVerificationRights(principal);
            })
        .orElse(true); // Allow service to handle 404
  }
}
