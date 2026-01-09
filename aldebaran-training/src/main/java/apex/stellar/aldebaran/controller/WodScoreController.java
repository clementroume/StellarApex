package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.service.WodScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for managing Athlete Performance (Scores).
 *
 * <p>Provides endpoints for athletes to log their workout results. Input data is accepted in
 * user-preferred units (e.g., Lbs), processed by the service, and stored in normalized system units
 * (Kg).
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
  public ResponseEntity<Page<WodScoreResponse>> getMyScores(
      @Parameter(description = "Filter by WOD ID") @RequestParam(required = false) Long wodId,
      @Parameter(description = "Pagination") @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(scoreService.getMyScores(wodId, pageable));
  }

  /**
   * Logs a new performance result for a specific WOD.
   *
   * @param request The score submission data (WOD ID, Date, Result, Scaling).
   * @return The persisted score with PR status, returned with HTTP 201 Created.
   */
  @PostMapping
  @Operation(summary = "Log score", description = "Logs a performance result for a specific WOD.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Score logged successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error - Score data does not match WOD type",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "WOD not found - The ID does not exist",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - User is not authenticated",
            content = @Content(schema = @Schema(hidden = true)))
      })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Fran Score Example",
                      value =
                          """
          { "wodId": 101, "date": "2023-10-25", "timeMinutes": 2, "timeSeconds": 30, "scaling": "RX" }
          """)))
  public ResponseEntity<WodScoreResponse> logScore(@Valid @RequestBody WodScoreRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(scoreService.logScore(request));
  }

  /**
   * Deletes a specific score entry.
   *
   * @param id The unique identifier of the score to delete.
   * @return HTTP 204 No Content on success.
   */
  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete score",
      description = "Removes a performance log (User must own it).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Score deleted successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Score not found - The ID does not exist",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - You can only delete your own scores",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - User is not authenticated",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> deleteScore(@PathVariable Long id) {
    scoreService.deleteScore(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Retrieves the rank and percentile of a specific score.
   *
   * @param id The ID of the score to compare.
   * @return The comparison metrics.
   */
  @GetMapping("/{id}/compare")
  @Operation(
      summary = "Compare score",
      description = "Calculates rank and percentile for a score against the global leaderboard.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Comparison retrieved"),
        @ApiResponse(
            responseCode = "404",
            description = "Score not found - The ID does not exist",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<ScoreComparisonResponse> compareScore(@PathVariable Long id) {
    return ResponseEntity.ok(scoreService.compareScore(id));
  }

  /**
   * Retrieves the leaderboard for a specific WOD.
   *
   * @param wodId The WOD ID.
   * @param scaling The scaling level (default RX).
   * @param pageable Pagination info.
   * @return A page of scores sorted by best performance.
   */
  @GetMapping("/leaderboard/{wodId}")
  @Operation(
      summary = "Get Leaderboard",
      description = "Retrieves top scores (PRs only) for a WOD.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Leaderboard retrieved"),
        @ApiResponse(responseCode = "404", description = "WOD not found")
      })
  public ResponseEntity<Page<WodScoreResponse>> getLeaderboard(
      @PathVariable Long wodId,
      @RequestParam(defaultValue = "RX") ScalingLevel scaling,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(scoreService.getLeaderboard(wodId, scaling, pageable));
  }
}
