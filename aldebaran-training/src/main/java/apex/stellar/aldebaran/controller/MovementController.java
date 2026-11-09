package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.MovementReferenceData;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.dto.MovementVariantResponse;
import apex.stellar.aldebaran.dto.UsualNameOptionResponse;
import apex.stellar.aldebaran.service.MovementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
   * Retrieves the movement catalog.
   *
   * @return A list of lightweigth {@link MovementSummaryResponse} objects.
   */
  @GetMapping
  public ResponseEntity<List<MovementSummaryResponse>> getMovements() {

    return ResponseEntity.ok(movementService.getAllMovements());
  }

  /**
   * Reads a movement for a chosen variant: usual name, roles, description and video.
   *
   * @param id The movement.
   * @param techniques The chosen techniques.
   * @param equipment The chosen equipment.
   * @return The variant.
   */
  @GetMapping("/{id}/variant")
  public ResponseEntity<MovementVariantResponse> getVariant(
      @PathVariable Long id,
      @RequestParam(defaultValue = "") Set<String> techniques,
      @RequestParam(defaultValue = "") Set<String> equipment) {
    return ResponseEntity.ok(movementService.getVariant(id, techniques, equipment));
  }

  /**
   * Lists the usual names of the catalog, for the movement search of the WOD forms.
   *
   * @return The usual names, each one a movement with techniques and equipment.
   */
  @GetMapping("/usual-names")
  public ResponseEntity<List<UsualNameOptionResponse>> getUsualNames() {
    return ResponseEntity.ok(movementService.getUsualNames());
  }

  /**
   * Retrieves detailed information about a specific movement by its ID.
   *
   * @param id The ID of the movement
   * @return The corresponding {@link MovementResponse}
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
