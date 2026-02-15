package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.dto.MovementResponse;
import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.service.MovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
  @Operation(
      summary = "Search movements",
      description = "Search exercises by name (autocomplete). Returns lightweight summaries.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Search successful"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<MovementSummaryResponse>> searchMovements(
      @Parameter(description = "Search term") @RequestParam(defaultValue = "") String query) {
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
  @Operation(
      summary = "Get movement details",
      description = "Retrieves full details including anatomy, descriptions, and media.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Movement details retrieved"),
        @ApiResponse(
            responseCode = "404",
            description = "Movement not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<MovementResponse> getMovement(@PathVariable String id) {
    return ResponseEntity.ok(movementService.getMovement(id));
  }

  /**
   * Retrieves movements filtered by functional category.
   *
   * <p>Defined with a specific path prefix to avoid ambiguity with ID lookup.
   *
   * @param category The category to filter by (e.g., DEADLIFT).
   * @return A list of movement summaries.
   */
  @GetMapping("/category/{category}")
  @Operation(
      summary = "Filter by category",
      description = "Retrieves movements for a specific functional category.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Movements retrieved"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<MovementSummaryResponse>> getMovementsByCategory(
      @PathVariable Category category) {
    return ResponseEntity.ok(movementService.getMovementsByCategory(category));
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
  @Operation(
      summary = "Create movement",
      description = "Adds a new exercise to the catalog (Admin only).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Movement created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Back Squat Example",
                      value =
                          """
          {
            "name": "Back Squat", "nameAbbreviation": "BS", "category": "SQUAT", "equipment": ["BARBELL", "PLATES"],
            "techniques": [], "muscles": [{"medicalName": "Quadriceps Femoris", "role": "AGONIST", "impactFactor": 1.0}],
            "involvesBodyweight": true, "bodyweightFactor": 1.0
          }""")))
  public ResponseEntity<MovementResponse> createMovement(
      @Valid @RequestBody MovementRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(movementService.createMovement(request));
  }

  /**
   * Updates an existing movement definition.
   *
   * <p><b>Security:</b> Restricted to users with the {@code ADMIN} role.
   *
   * <p>This operation performs a full update of the movement, including re-linking muscle
   * relationships.
   *
   * @param id The ID of the movement to update.
   * @param request The updated movement data.
   * @return The updated movement details.
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Update movement",
      description = "Updates an existing exercise definition (Admin only).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Movement updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Movement not found",
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
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              examples =
                  @ExampleObject(
                      name = "Update Example",
                      value =
                          """
          {
            "name": "Back Squat (High Bar)", "nameAbbreviation": "HBBS", "category": "SQUAT", "equipment": ["BARBELL"],
            "techniques": ["HIGH_BAR"], "muscles": [], "involvesBodyweight": true, "bodyweightFactor": 1.0
          }""")))
  public ResponseEntity<MovementResponse> updateMovement(
      @PathVariable String id, @Valid @RequestBody MovementRequest request) {
    return ResponseEntity.ok(movementService.updateMovement(id, request));
  }
}
