package apex.stellar.aldebaran.security;

import static apex.stellar.aldebaran.security.SecurityService.SCORE_VERIFY;

import apex.stellar.aldebaran.client.AntaresClient;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.exception.AntaresUnavailableException;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.util.Collection;
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
 *   <li><b>Participation (Self):</b> Athletes can log scores for Public WODs, the WODs of any of
 *       their member Gyms, or their Private WODs; a session WOD once its session's content is shown
 *       to them.
 *   <li><b>Moderation (Staff):</b> users holding SCORE_VERIFY in the score's gym can correct or
 *       delete the scores performed there (errors, cheating).
 *   <li><b>Competition (Staff):</b> on a competition WOD, users holding SCORE_VERIFY in its gym
 *       log scores for the gym's members (membership confirmed by Antares) and verify the scores
 *       submitted for verification (never their own). A verified score is locked for its athlete,
 *       staff included.
 *   <li><b>Logging for someone else</b> anywhere else: admins only.
 * </ul>
 *
 * <p><b>Strategy regarding "Not Found":</b> Methods return {@code true} if the resource ID is not
 * found. This delegates the responsibility to the Service layer to throw a proper {@code
 * ResourceNotFoundException} (HTTP 404), rather than triggering a generic {@code
 * AccessDeniedException} (HTTP 403).
 */
@Component("scoreSecurity")
@RequiredArgsConstructor
public class ScoreSecurity {

  private final ScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final SecurityService securityService;
  private final AntaresClient antaresClient;
  private final SessionSecurity sessionSecurity;

  /**
   * Evaluates if the authenticated user is authorized to view a specific score.
   *
   * @param scoreId The unique identifier of the score.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the score is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canView(Long scoreId, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
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
                return principal.isMemberOf(wod.getGymId());
              }

              // 4. Private WOD -> Visible only to the owner (already covered by step 1)
              return false;
            })
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user is authorized to create (log) a score: their own score on a
   * readable WOD, or, on a competition WOD, the score of a member of its gym when they hold
   * SCORE_VERIFY there (Antares confirms the membership).
   *
   * @param request The score creation payload.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the WOD is not found (deferring to 404).
   * @throws AntaresUnavailableException if the membership cannot be confirmed (never authorized
   *     by default).
   */
  public boolean canCreate(ScoreRequest request, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
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
                // The gym of a public or personal WOD score must be one of the user's gyms
                if (wod.getGymId() == null && !canUseGym(request.gymId(), principal)) {
                  return false;
                }
                // 0. Session WOD: its session's content must be shown to the athlete
                if (!wod.isStandalone()) {
                  return sessionSecurity.canReadWodContent(wod.getId(), principal);
                }
                // 1. Public: Open to all
                if (wod.isPublic()) {
                  return true;
                }
                // 2. Gym: Must be a member of that gym
                if (wod.getGymId() != null) {
                  return principal.isMemberOf(wod.getGymId());
                }
                // 3. Private: Author only
                return Objects.equals(wod.getAuthorId(), principal.getId());
              }

              // CASE B: Logging for someone else: competition staff, for a member of the gym
              return wod.isCompetition()
                  && securityService.hasGymPermission(principal, wod.getGymId(), SCORE_VERIFY)
                  && antaresClient.isGymMember(wod.getGymId(), targetUserId);
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

    if (securityService.isAdmin(principal)) {
      return true;
    }

    return scoreRepository
        .findById(scoreId)
        .map(score -> canModify(score.getUserId(), gymOf(score), score.isVerified(), principal))
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user may update or delete an already loaded score: its athlete
   * while it is not verified, a staff member holding SCORE_VERIFY in the score's gym, or an admin.
   *
   * @param score The score to evaluate.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized.
   */
  public boolean canModify(ScoreResponse score, AldebaranUserPrincipal principal) {
    return canModify(score.userId(), gymOf(score), score.verifiedAt() != null, principal);
  }

  /**
   * Evaluates if the authenticated user may verify a score or withdraw its verification.
   *
   * @param scoreId The unique identifier of the score.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized or if the score is not found (deferring to 404).
   */
  @Transactional(readOnly = true)
  public boolean canVerify(Long scoreId, AldebaranUserPrincipal principal) {

    if (securityService.isAdmin(principal)) {
      return true;
    }

    return scoreRepository
        .findById(scoreId)
        .map(
            score ->
                canVerify(
                    score.getUserId(),
                    gymOf(score),
                    score.getWod().isCompetition() && score.isVerificationRequested(),
                    principal))
        .orElse(true); // Allow service to handle 404
  }

  /**
   * Evaluates if the authenticated user may verify an already loaded score: staff holding
   * SCORE_VERIFY in the gym of a competition WOD, except on their own score, or an admin.
   *
   * @param score The score to evaluate.
   * @param principal The authenticated user context.
   * @return {@code true} if authorized.
   */
  public boolean canVerify(ScoreResponse score, AldebaranUserPrincipal principal) {
    return canVerify(
        score.userId(),
        gymOf(score),
        score.wodSummary().competition() && score.verificationRequested(),
        principal);
  }

  /**
   * Evaluates if the authenticated user may attach a score to, or filter a leaderboard on, the
   * given gym: one of their gyms, or any gym for an admin.
   *
   * @param gymId The gym, or null (no gym).
   * @param principal The authenticated user context.
   * @return {@code true} if allowed.
   */
  public boolean canUseGym(Long gymId, AldebaranUserPrincipal principal) {
    return gymId == null || securityService.isAdmin(principal) || principal.isMemberOf(gymId);
  }

  /**
   * Evaluates if the authenticated user may filter a leaderboard on the given gyms: all of them
   * among their gyms (any gym for an admin).
   *
   * @param gymIds The gyms, or null / empty (no filter).
   * @param principal The authenticated user context.
   * @return {@code true} if allowed.
   */
  public boolean canUseGyms(Collection<Long> gymIds, AldebaranUserPrincipal principal) {
    return gymIds == null || gymIds.stream().allMatch(gymId -> canUseGym(gymId, principal));
  }

  /** Gym of a score for moderation: where it was performed, else the WOD's gym. */
  private static Long gymOf(Score score) {
    return score.getGymId() != null ? score.getGymId() : score.getWod().getGymId();
  }

  private static Long gymOf(ScoreResponse score) {
    return score.gymId() != null ? score.gymId() : score.wodSummary().gymId();
  }

  private boolean canModify(
      Long athleteId, Long gymId, boolean verified, AldebaranUserPrincipal principal) {
    if (securityService.isAdmin(principal)) {
      return true;
    }
    // 1. Self-Correction: athletes modify their own scores until they are verified (staff
    // included: nobody changes their own verified score)
    if (Objects.equals(athleteId, principal.getId())) {
      return !verified;
    }
    // 2. Staff Moderation: SCORE_VERIFY in the gym where the score was performed
    return securityService.hasGymPermission(principal, gymId, SCORE_VERIFY);
  }

  private boolean canVerify(
      Long athleteId, Long gymId, boolean submitted, AldebaranUserPrincipal principal) {
    if (securityService.isAdmin(principal)) {
      return true;
    }
    // Judging: scores submitted on competition WODs only, by the gym's staff, never one's own
    return submitted
        && !Objects.equals(athleteId, principal.getId())
        && securityService.hasGymPermission(principal, gymId, SCORE_VERIFY);
  }
}
