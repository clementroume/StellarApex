package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.service.WodScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
   * @return A list of the user's scores, ordered by date.
   */
  @GetMapping("/me")
  @Operation(
      summary = "My history",
      description = "Retrieves the score history of the current user.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "History retrieved"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<WodScoreResponse>> getMyScores() {
    return ResponseEntity.ok(scoreService.getMyScores());
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
            description = "Validation error (e.g. missing time for FOR_TIME WOD)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "WOD not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
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
            description = "Score not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Not owner",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
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
            description = "Score not found",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<ScoreComparisonResponse> compareScore(@PathVariable Long id) {
    return ResponseEntity.ok(scoreService.compareScore(id));
  }
}
