package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.service.MuscleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.RequestParam;
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
   * Retrieves the anatomical catalog, optionally filtered by muscle group.
   *
   * <p>If the 'group' parameter is provided, returns only muscles belonging to that group.
   * Otherwise, returns the full catalog.
   *
   * @param group (Optional) The anatomical group to filter by (e.g., "LEGS").
   * @return A list of {@link MuscleResponse} objects.
   */
  @GetMapping
  @Operation(
      summary = "List muscles",
      description = "Retrieves the full anatomical catalog or filters by muscle group.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Muscles retrieved"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<MuscleResponse>> getMuscles(
      @Parameter(description = "Filter by anatomical group") @RequestParam(required = false)
          MuscleGroup group) {

    if (group != null) {
      return ResponseEntity.ok(muscleService.getMusclesByGroup(group));
    }
    return ResponseEntity.ok(muscleService.getAllMuscles());
  }

  /**
   * Retrieves details of a specific muscle by its medical name.
   *
   * <p>This uses the Business Key (Medical Name) as it is the primary identifier used in movement
   * prescriptions.
   *
   * @param medicalName The Latin/Medical name of the muscle (e.g., "Pectoralis Major").
   * @return The detailed muscle response.
   */
  @GetMapping("/{medicalName}")
  @Operation(
      summary = "Get muscle details",
      description = "Retrieves a single muscle by its Medical Name (Business Key).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Muscle details retrieved"),
        @ApiResponse(
            responseCode = "404",
            description = "Muscle not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<MuscleResponse> getMuscle(@PathVariable String medicalName) {

    return ResponseEntity.ok(muscleService.getMuscle(medicalName));
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
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Muscle created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - Name already exists",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin access required",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<MuscleResponse> createMuscle(@Valid @RequestBody MuscleRequest request) {

    return ResponseEntity.status(HttpStatus.CREATED).body(muscleService.createMuscle(request));
  }

  /**
   * Updates an existing muscle definition.
   *
   * <p><b>Security:</b> Restricted to users with the {@code ADMIN} role.
   *
   * <p>Uses the technical ID to target the resource, allowing updates to the Medical Name if
   * necessary.
   *
   * @param id The unique identifier of the muscle to update.
   * @param request The updated data.
   * @return The updated muscle resource.
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update muscle", description = "Updates an existing muscle (Admin only).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Muscle updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Muscle not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - Name already exists",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin access required",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<MuscleResponse> updateMuscle(
      @PathVariable Long id, @Valid @RequestBody MuscleRequest request) {
    
    return ResponseEntity.ok(muscleService.updateMuscle(id, request));
  }
}
