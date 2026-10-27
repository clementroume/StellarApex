package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.ScoreReferenceData;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.ScoreSecurity;
import apex.stellar.aldebaran.service.ScoreCapabilityService;
import apex.stellar.aldebaran.service.ScoreService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for managing Athlete Performance (Scores).
 *
 * <p>Secured by {@link ScoreSecurity}.
 */
@RestController
@RequestMapping("/aldebaran/scores")
@RequiredArgsConstructor
@Tag(name = "Performance", description = "Athlete score logging and history")
public class ScoreController {

  private final ScoreService scoreService;
  private final ScoreCapabilityService scoreCapabilityService;

  /**
   * Retrieves the score history for the currently authenticated user.
   *
   * @param wodId Optional WOD ID to filter history (e.g., "Show me my progress on Fran").
   * @param pageable Pagination info.
   * @param principal The authenticated user.
   * @return A page of the user's scores, with their capabilities.
   */
  @GetMapping("/me")
  public ResponseEntity<Slice<ScoreResponse>> getMyScores(
      @RequestParam(required = false) Long wodId,
      @PageableDefault(size = 20) Pageable pageable,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        scoreService
            .getMyScores(wodId, pageable)
            .map(score -> scoreCapabilityService.forViewer(score, principal)));
  }

  /**
   * Logs a new performance result (Score).
   *
   * <p><b>Security:</b>
   *
   * <ul>
   *   <li>Users can log scores for themselves on the WODs they can read.
   *   <li>On a competition WOD, staff holding SCORE_VERIFY in its gym can log the score of a member
   *       of that gym ({@code userId}); Antares confirms the membership (503 if unavailable). Such
   *       a score is verified at once.
   *   <li>Admins can log scores for anyone.
   * </ul>
   *
   * @param request The score details.
   * @param principal The authenticated user.
   * @return The created score.
   */
  @PostMapping
  @PreAuthorize("@scoreSecurity.canCreate(#request, principal)")
  public ResponseEntity<ScoreResponse> logScore(
      @Valid @RequestBody ScoreRequest request, @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(scoreCapabilityService.forViewer(scoreService.logScore(request), principal));
  }

  /**
   * Updates an existing score.
   *
   * <p><b>Security:</b>
   *
   * <ul>
   *   <li>Owners can update their own scores until they are verified.
   *   <li>Staff holding SCORE_VERIFY in the score's gym can correct their athletes' scores.
   *   <li>Admins can update any score.
   * </ul>
   *
   * <p>The athlete and the WOD of a score never change (400 if {@code wodId} differs).
   *
   * @param id The ID of the score to update.
   * @param request The updated score details.
   * @param principal The authenticated user.
   * @return The updated score.
   */
  @PutMapping("/{id}")
  @PreAuthorize(
      "@scoreSecurity.canModify(#id, principal)"
          + " and @scoreSecurity.canUseGym(#request.gymId(), principal)")
  public ResponseEntity<ScoreResponse> updateScore(
      @PathVariable Long id, @Valid @RequestBody ScoreRequest request, @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        scoreCapabilityService.forViewer(scoreService.updateScore(id, request), principal));
  }

  /**
   * Deletes a performance log.
   *
   * <p><b>Security:</b> Follows the same rules as Update.
   *
   * @param id The ID of the score to delete.
   * @return HTTP 204 No Content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@scoreSecurity.canModify(#id, principal)")
  public ResponseEntity<Void> deleteScore(@PathVariable Long id) {

    scoreService.deleteScore(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Verifies a score of a competition WOD (judging).
   *
   * <p><b>Security:</b> staff holding SCORE_VERIFY in the gym of the competition WOD, never on
   * their own score, or admins. A verified score is locked for its athlete.
   *
   * @param id The ID of the score.
   * @param principal The authenticated user.
   * @return The verified score.
   */
  @PostMapping("/{id}/verify")
  @PreAuthorize("@scoreSecurity.canVerify(#id, principal)")
  public ResponseEntity<ScoreResponse> verifyScore(
      @PathVariable Long id, @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        scoreCapabilityService.forViewer(scoreService.setVerified(id, true), principal));
  }

  /**
   * Withdraws the verification of a score, which unlocks it for its athlete.
   *
   * <p><b>Security:</b> same rules as verification.
   *
   * @param id The ID of the score.
   * @param principal The authenticated user.
   * @return The score, no longer verified.
   */
  @DeleteMapping("/{id}/verify")
  @PreAuthorize("@scoreSecurity.canVerify(#id, principal)")
  public ResponseEntity<ScoreResponse> unverifyScore(
      @PathVariable Long id, @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        scoreCapabilityService.forViewer(scoreService.setVerified(id, false), principal));
  }

  /**
   * Calculates the rank and percentile of a specific score against the Personal Records of the
   * other athletes at the same scaling level.
   *
   * @param id The ID of the score to analyze.
   * @return The comparison metrics.
   */
  @GetMapping("/{id}/compare")
  @PreAuthorize("@scoreSecurity.canView(#id, principal)")
  public ResponseEntity<ScoreComparisonResponse> compareScore(@PathVariable Long id) {

    return ResponseEntity.ok(scoreService.compareScore(id));
  }

  /**
   * Retrieves the leaderboard for a specific WOD.
   *
   * <p>Returns the best score (PR) of each athlete whose PR is at the given scaling level. Other
   * athletes' comments and scaling notes are hidden unless the viewer can moderate the score.
   *
   * @param id The ID of the WOD.
   * @param scaling The scaling level to filter by (default: RX).
   * @param gymIds Optional gyms (all among the user's gyms, 403 otherwise): only the scores
   *     performed there, e.g. "my box(es)" or "my program(s)"; worldwide when absent.
   * @param verifiedOnly Only verified scores (competition WODs); default false.
   * @param pageable Pagination info.
   * @param principal The authenticated user.
   * @return A page of top scores.
   */
  @GetMapping("/leaderboard/{id}")
  @PreAuthorize(
      "@wodSecurity.canRead(#id, principal) and @scoreSecurity.canUseGyms(#gymIds, principal)")
  public ResponseEntity<Slice<ScoreResponse>> getLeaderboard(
      @PathVariable Long id,
      @RequestParam(defaultValue = "RX") ScalingLevel scaling,
      @RequestParam(required = false) List<Long> gymIds,
      @RequestParam(defaultValue = "false") boolean verifiedOnly,
      @PageableDefault(size = 20) Pageable pageable,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        scoreService
            .getLeaderboard(id, scaling, gymIds, verifiedOnly, pageable)
            .map(score -> scoreCapabilityService.forViewer(score, principal)));
  }

  /** Retrieves reference data (scaling levels) for score submission forms. */
  @GetMapping("/reference-data")
  public ResponseEntity<ScoreReferenceData> getReferenceData() {

    return ResponseEntity.ok(scoreService.getReferenceData());
  }
}
