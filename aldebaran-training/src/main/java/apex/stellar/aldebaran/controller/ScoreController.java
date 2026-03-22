package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.ScoreReferenceData;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.security.ScoreSecurity;
import apex.stellar.aldebaran.service.ScoreService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  /**
   * Retrieves the score history for the currently authenticated user.
   *
   * @param wodId Optional WOD ID to filter history (e.g., "Show me my progress on Fran").
   * @param pageable Pagination info.
   * @return A page of the user's scores.
   */
  @GetMapping("/me")
  public ResponseEntity<Slice<ScoreResponse>> getMyScores(
      @RequestParam(required = false) Long wodId, @PageableDefault(size = 20) Pageable pageable) {

    return ResponseEntity.ok(scoreService.getMyScores(wodId, pageable));
  }

  /**
   * Logs a new performance result (Score).
   *
   * <p><b>Security:</b>
   *
   * <ul>
   *   <li>Users can log scores for themselves.
   *   <li>Coaches can log scores for athletes belonging to their gym.
   *   <li>Admins can log scores for anyone.
   * </ul>
   *
   * @param request The score details.
   * @return The created score.
   */
  @PostMapping
  @PreAuthorize("@scoreSecurity.canCreate(#request, principal)")
  public ResponseEntity<ScoreResponse> logScore(@Valid @RequestBody ScoreRequest request) {

    return ResponseEntity.status(HttpStatus.CREATED).body(scoreService.logScore(request));
  }

  /**
   * Updates an existing score.
   *
   * <p><b>Security:</b>
   *
   * <ul>
   *   <li>Owners can update their own scores.
   *   <li>Coaches/Owners of the gym where the WOD was created can moderate scores.
   *   <li>Admins can update any score.
   * </ul>
   *
   * @param id The ID of the score to update.
   * @param request The updated score details.
   * @return The updated score.
   */
  @PutMapping("/{id}")
  @PreAuthorize("@scoreSecurity.canModify(#id, principal)")
  public ResponseEntity<ScoreResponse> updateScore(
      @PathVariable Long id, @Valid @RequestBody ScoreRequest request) {

    return ResponseEntity.ok(scoreService.updateScore(id, request));
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
   * Calculates the rank and percentile of a specific score against the global population.
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
   * <p>Returns only the best score (PR) per athlete for the given scaling level.
   *
   * @param id The ID of the WOD.
   * @param scaling The scaling level to filter by (default: RX).
   * @param pageable Pagination info.
   * @return A page of top scores.
   */
  @GetMapping("/leaderboard/{id}")
  @PreAuthorize("@wodSecurity.canRead(#id, principal)")
  public ResponseEntity<Slice<ScoreResponse>> getLeaderboard(
      @PathVariable Long id,
      @RequestParam(defaultValue = "RX") ScalingLevel scaling,
      @PageableDefault(size = 20) Pageable pageable) {

    return ResponseEntity.ok(scoreService.getLeaderboard(id, scaling, pageable));
  }

  /** Retrieves reference data (scaling levels) for score submission forms. */
  @GetMapping("/reference-data")
  public ResponseEntity<ScoreReferenceData> getReferenceData() {

    return ResponseEntity.ok(scoreService.getReferenceData());
  }
}
