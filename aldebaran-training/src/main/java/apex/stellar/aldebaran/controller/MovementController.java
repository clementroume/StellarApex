package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.MovementReferenceData;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.service.MovementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for the Movement Catalog.
 *
 * <p>Exposes endpoints to manage exercise definitions. Read operations are generally accessible to
 * authenticated users (for searching and viewing), while write operations (Create/Update) are
 * strictly restricted to administrators.
 */
@RestController
@RequestMapping("/aldebaran/movements")
@RequiredArgsConstructor
@Tag(name = "Movements", description = "Exercise catalog management")
public class MovementController {

  private final MovementService movementService;

  /**
   * Searches for movements by name.
   *
   * <p>This endpoint returns a list of lightweight {@link MovementSummaryResponse} objects,
   * optimized for autocomplete dropdowns and search results. It does not load heavy relationships
   * like anatomical details.
   *
   * @param query The search string (case-insensitive). Defaults to empty (returns all).
   * @return A list of matching movements.
   */
  @GetMapping
  public ResponseEntity<List<MovementSummaryResponse>> searchMovements(
      @RequestParam(defaultValue = "") String query) {

    return ResponseEntity.ok(movementService.searchMovements(query));
  }

  /**
   * Retrieves detailed information about a specific movement.
   *
   * <p>Returns the full {@link MovementResponse}, including internationalized descriptions,
   * anatomical breakdowns (muscles involved), and media links.
   *
   * @param id The unique business ID of the movement (e.g., "WL-SQ-001").
   * @return The complete movement details.
   */
  @GetMapping("/{id}")
  public ResponseEntity<MovementResponse> getMovement(@PathVariable Long id) {

    return ResponseEntity.ok(movementService.getMovement(id));
  }

  /**
   * Creates a new movement in the catalog.
   *
   * <p><b>Security:</b> Restricted to users with the {@code ADMIN} role.
   *
   * @param request The movement creation payload.
   * @return The created movement with its generated ID and HTTP 201 Created status.
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<MovementResponse> createMovement(
      @Valid @RequestBody MovementRequest request) {

    return ResponseEntity.status(HttpStatus.CREATED).body(movementService.createMovement(request));
  }

  /**
   * Updates an existing movement definition.
   *
   * @param id The ID of the movement to update.
   * @param request The updated movement data.
   * @return The updated movement details.
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<MovementResponse> updateMovement(
      @PathVariable Long id, @Valid @RequestBody MovementRequest request) {

    return ResponseEntity.ok(movementService.updateMovement(id, request));
  }

  /**
   * Updates an existing movement definition.
   *
   * @param id The ID of the movement to update.
   * @return An HTTP 204 no content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteMovement(@PathVariable Long id) {

    movementService.deleteMovement(id);
    return ResponseEntity.noContent().build();
  }

  /** Retrieves structured reference data (categories, equipments, techniques) for UI forms. */
  @GetMapping("/reference-data")
  public ResponseEntity<MovementReferenceData> getReferenceData() {

    return ResponseEntity.ok(movementService.getReferenceData());
  }
}
