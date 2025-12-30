package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.service.MuscleService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for managing the Anatomical Muscle Catalog.
 *
 * <p>Provides endpoints to retrieve reference data regarding human anatomy (muscles). While read
 * operations are open to authenticated users to support features like "Targeted Muscle" filtering,
 * write operations are strictly restricted to Administrators to maintain data integrity.
 */
@RestController
@RequestMapping("/aldebaran/muscles")
@RequiredArgsConstructor
@Tag(name = "Anatomy", description = "Muscle reference data management")
public class MuscleController {

  private final MuscleService muscleService;

  /**
   * Retrieves the full anatomical catalog.
   *
   * <p>Returns a flat list of all defined muscles. This data is intended to be cached by clients as
   * it changes very infrequently.
   *
   * @return A list of {@link MuscleResponse} containing internationalized names and descriptions.
   */
  @GetMapping
  @Operation(summary = "List all muscles", description = "Retrieves the full anatomical catalog.")
  public ResponseEntity<List<MuscleResponse>> getAllMuscles() {
    return ResponseEntity.ok(muscleService.getAllMuscles());
  }

  /**
   * Creates a new muscle entry in the catalog.
   *
   * <p><b>Security:</b> Restricted to users with the {@code ADMIN} role.
   *
   * <p>Enforces unique constraints on the medical name to prevent duplicates.
   *
   * @param request The muscle creation payload.
   * @return The created muscle resource with HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Create muscle",
      description = "Adds a new muscle to the catalog (Admin only).")
  public ResponseEntity<MuscleResponse> createMuscle(@Valid @RequestBody MuscleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(muscleService.createMuscle(request));
  }

  /**
   * Updates an existing muscle definition.
   *
   * <p><b>Security:</b> Restricted to users with the {@code ADMIN} role.
   *
   * @param id The unique identifier of the muscle to update.
   * @param request The updated data.
   * @return The updated muscle resource.
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update muscle", description = "Updates an existing muscle (Admin only).")
  public ResponseEntity<MuscleResponse> updateMuscle(
      @PathVariable Long id, @Valid @RequestBody MuscleRequest request) {
    return ResponseEntity.ok(muscleService.updateMuscle(id, request));
  }
}
