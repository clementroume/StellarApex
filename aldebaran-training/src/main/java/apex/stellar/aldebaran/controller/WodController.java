package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.service.WodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for managing Workout of the Day (WOD) definitions.
 *
 * <p>This controller acts as the entry point for the "Recipe" aspect of the application. It allows
 * users to browse available workouts and enables Coaches/Admins to define new workout structures,
 * including their specific movements and scoring logic.
 */
@RestController
@RequestMapping("/aldebaran/wods")
@RequiredArgsConstructor
@Tag(name = "Workouts", description = "WOD definition management")
public class WodController {

  private final WodService wodService;

  /**
   * Retrieves a list of all available WODs.
   *
   * <p>Returns lightweight {@link WodSummaryResponse} objects containing only essential metadata
   * (Title, Type, Score Type) to optimize bandwidth for list views.
   *
   * @return A list of WOD summaries.
   */
  @GetMapping
  @Operation(summary = "List WODs", description = "Retrieves summaries of available workouts.")
  public ResponseEntity<List<WodSummaryResponse>> getAllWods() {
    return ResponseEntity.ok(wodService.findAllWods());
  }

  /**
   * Retrieves the detailed definition of a specific WOD.
   *
   * <p>The response includes the full "Recipe", including the list of prescribed movements,
   * repetition schemes, weights, and coaching notes.
   *
   * @param id The unique identifier of the WOD.
   * @return The detailed WOD definition.
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get WOD details", description = "Retrieves the full recipe of a workout.")
  public ResponseEntity<WodResponse> getWod(@PathVariable Long id) {
    return ResponseEntity.ok(wodService.getWod(id));
  }

  /**
   * Creates a new WOD definition.
   *
   * <p><b>Security:</b> Restricted to users with the {@code ADMIN} or {@code COACH} role.
   *
   * <p>The payload must include valid references to existing Movements.
   *
   * @param request The WOD creation payload.
   * @return The created WOD with its generated ID and HTTP 201 Created status.
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('COACH')")
  @Operation(summary = "Create WOD", description = "Defines a new workout (Admin/Coach only).")
  public ResponseEntity<WodResponse> createWod(@Valid @RequestBody WodRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(wodService.createWod(request));
  }
}
