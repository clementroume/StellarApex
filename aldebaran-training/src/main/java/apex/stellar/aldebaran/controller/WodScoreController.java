package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.service.WodScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * <p>Secured by {@link apex.stellar.aldebaran.security.WodScoreSecurity}.
 */
@RestController
@RequestMapping("/aldebaran/scores")
@RequiredArgsConstructor
@Tag(name = "Performance", description = "Athlete score logging and history")
public class WodScoreController {

  private final WodScoreService scoreService;

  /**
   * Retrieves the score history for the currently authenticated user.
   *
   * @param wodId Optional WOD ID to filter history (e.g., "Show me my progress on Fran").
   * @param pageable Pagination info.
   * @return A page of the user's scores.
   */
  @GetMapping("/me")
  @Operation(
      summary = "My history",
      description = "Retrieves the score history of the current user. Can be filtered by WOD.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "History retrieved"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - User is not authenticated",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Slice<WodScoreResponse>> getMyScores(
      @Parameter(description = "Filter by WOD ID") @RequestParam(required = false) Long wodId,
      @Parameter(description = "Pagination (page, size)") @PageableDefault(size = 20)
          Pageable pageable) {
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
   * @param principal The authenticated user.
   * @return The created score.
   */
  @PostMapping
  @PreAuthorize("@wodScoreSecurity.canCreate(#request, principal)")
  @Operation(summary = "Log score", description = "Logs a performance result.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Score logged successfully"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Logging for others without permission",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<WodScoreResponse> logScore(
      @Valid @RequestBody WodScoreRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {
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
   * @param principal The authenticated user.
   * @return The updated score.
   */
  @PutMapping("/{id}")
  @PreAuthorize("@wodScoreSecurity.canModify(#id, principal)")
  @Operation(
      summary = "Update score",
      description = "Updates an existing score (Owner, Admin, or Coach of the gym).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Score updated successfully"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - No write access to this score",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Score not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<WodScoreResponse> updateScore(
      @PathVariable Long id,
      @Valid @RequestBody WodScoreRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {
    return ResponseEntity.ok(scoreService.updateScore(id, request));
  }

  /**
   * Deletes a performance log.
   *
   * <p><b>Security:</b> Follows the same rules as Update.
   *
   * @param id The ID of the score to delete.
   * @param principal The authenticated user.
   * @return HTTP 204 No Content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@wodScoreSecurity.canModify(#id, principal)")
  @Operation(summary = "Delete score", description = "Removes a performance log.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Score deleted successfully"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - No write access to this score",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Score not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> deleteScore(
      @PathVariable Long id, @AuthenticationPrincipal AldebaranUserPrincipal principal) {
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
  @PreAuthorize("@wodScoreSecurity.canView(#id, principal)")
  @Operation(summary = "Compare score", description = "Calculates rank and percentile.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Comparison calculated"),
        @ApiResponse(
            responseCode = "404",
            description = "Score not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<ScoreComparisonResponse> compareScore(@PathVariable Long id) {
    return ResponseEntity.ok(scoreService.compareScore(id));
  }

  /**
   * Retrieves the leaderboard for a specific WOD.
   *
   * <p>Returns only the best score (PR) per athlete for the given scaling level.
   *
   * @param wodId The ID of the WOD.
   * @param scaling The scaling level to filter by (default: RX).
   * @param pageable Pagination info.
   * @return A page of top scores.
   */
  @GetMapping("/leaderboard/{wodId}")
  @PreAuthorize("@wodSecurity.canRead(#wodId, principal)")
  @Operation(
      summary = "Get Leaderboard",
      description = "Retrieves top scores (PRs only) for a WOD.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Leaderboard retrieved"),
        @ApiResponse(
            responseCode = "404",
            description = "WOD not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Slice<WodScoreResponse>> getLeaderboard(
      @PathVariable Long wodId,
      @Parameter(description = "Scaling level (RX, SCALED...)") @RequestParam(defaultValue = "RX")
          ScalingLevel scaling,
      @Parameter(description = "Pagination (page, size)") @PageableDefault(size = 20)
          Pageable pageable) {
    return ResponseEntity.ok(scoreService.getLeaderboard(wodId, scaling, pageable));
  }
}
