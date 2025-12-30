package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.service.WodScoreService;
import io.swagger.v3.oas.annotations.Operation;
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
 * <p>Provides endpoints for athletes to log their workout results, view their history, and manage
 * their own data. Security is strictly enforced to ensure users can only modify or delete their own
 * scores.
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
   * <p>The results are ordered by date (descending). Each entry includes a summary of the performed
   * WOD for context.
   *
   * @return A list of the user's scores.
   */
  @GetMapping("/me")
  @Operation(
      summary = "My history",
      description = "Retrieves the score history of the current user.")
  public ResponseEntity<List<WodScoreResponse>> getMyScores() {
    return ResponseEntity.ok(scoreService.getMyScores());
  }

  /**
   * Logs a new performance result for a specific WOD.
   *
   * <p>This operation automatically triggers Personal Record (PR) calculations based on the user's
   * previous history and the WOD's scoring type.
   *
   * @param request The score submission data (WOD ID, Date, Result, Scaling).
   * @return The persisted score with PR status, returned with HTTP 201 Created.
   */
  @PostMapping
  @Operation(summary = "Log score", description = "Logs a performance result for a specific WOD.")
  public ResponseEntity<WodScoreResponse> logScore(@Valid @RequestBody WodScoreRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(scoreService.logScore(request));
  }

  /**
   * Deletes a specific score entry.
   *
   * <p><b>Security:</b> Users can only delete scores that belong to them. Attempting to delete
   * another user's score will result in a 403 Forbidden error.
   *
   * @param id The unique identifier of the score to delete.
   * @return HTTP 204 No Content on success.
   */
  @DeleteMapping("/{id}")
  @Operation(
      summary = "Delete score",
      description = "Removes a performance log (User must own it).")
  public ResponseEntity<Void> deleteScore(@PathVariable Long id) {
    scoreService.deleteScore(id);
    return ResponseEntity.noContent().build();
  }
}
