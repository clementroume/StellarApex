package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.service.WodService;
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
import org.springframework.data.domain.Pageable;
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
 * REST Controller for managing Workout of the Day (WOD) definitions.
 *
 * <p>This controller acts as the entry point for the "Recipe" aspect of the application. It allows
 * users to browse available workouts and enables Coaches/Admins to define new workout structures.
 */
@RestController
@RequestMapping("/aldebaran/wods")
@RequiredArgsConstructor
@Tag(name = "Workouts", description = "WOD definition management")
public class WodController {

  private final WodService wodService;

  /**
   * Retrieves a list of available WODs with optional filtering and pagination.
   *
   * <p>Returns lightweight {@link WodSummaryResponse} objects containing only essential metadata to
   * optimize bandwidth.
   *
   * @param search Optional text to search in WOD titles.
   * @param type Optional filter for WOD type (e.g., AMRAP, FORTIME).
   * @param movementId Optional filter to find WODs containing a specific movement.
   * @param pageable Pagination info (page, size, sort). Defaults to 20 items per page.
   * @return A list of WOD summaries matching the criteria.
   */
  @GetMapping
  @Operation(
      summary = "List WODs",
      description = "Retrieves summaries of available workouts. Supports filtering by title, type, or specific movement.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "WODs retrieved"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - User is not authenticated",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<WodSummaryResponse>> getWods(
      @Parameter(description = "Search by title") @RequestParam(required = false) String search,
      @Parameter(description = "Filter by WOD Type") @RequestParam(required = false) WodType type,
      @Parameter(description = "Filter by Movement ID (e.g. 'WL-SQ-001')") @RequestParam(required = false) String movementId,
      @Parameter(description = "Pagination (page, size)") @PageableDefault(size = 20)
          Pageable pageable) {
    return ResponseEntity.ok(wodService.getWods(search, type, movementId, pageable));
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
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "WOD details retrieved"),
        @ApiResponse(
            responseCode = "404",
            description = "WOD not found - The ID does not exist",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - User is not authenticated",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<WodResponse> getWod(@PathVariable Long id) {
    return ResponseEntity.ok(wodService.getWodDetail(id));
  }

  /**
   * Creates a new WOD definition.
   *
   * <p><b>Security:</b> Restricted to users with the {@code ADMIN} or {@code COACH} role.
   *
   * @param request The WOD creation payload.
   * @param principal The authenticated user.
   * @return The created WOD with its generated ID and HTTP 201 Created status.
   */
  @PostMapping
  @PreAuthorize("@wodSecurity.canCreate(#request, principal)")
  @Operation(summary = "Create WOD", description = "Defines a new workout (Admin/Coach only).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "WOD created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error - Invalid input data",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires COACH or ADMIN role",
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
                      name = "Fran Example",
                      value =
                          """
          {
            "title": "Fran", "wodType": "FOR_TIME", "scoreType": "TIME", "isPublic": true, "repScheme": "21-15-9",
            "movements": [
              { "movementId": "WL-TR-001", "orderIndex": 1, "repsScheme": "21-15-9", "weight": 43.0, "weightUnit": "KG" },
              { "movementId": "GY-PU-001", "orderIndex": 2, "repsScheme": "21-15-9" }
            ]
          }""")))
  public ResponseEntity<WodResponse> createWod(
      @Valid @RequestBody WodRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {
    return ResponseEntity.status(HttpStatus.CREATED).body(wodService.createWod(request));
  }

  /**
   * Updates an existing WOD definition.
   *
   * <p>Replaces the existing movements and metadata with the provided request.
   *
   * @param id The ID of the WOD to update.
   * @param request The updated WOD payload.
   * @param principal The authenticated user.
   * @return The updated WOD response.
   */
  @PutMapping("/{id}")
  @PreAuthorize("@wodSecurity.canUpdate(#id, principal)")
  @Operation(
      summary = "Update WOD",
      description = "Updates an existing workout (Admin/Coach only).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "WOD updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error - Invalid input data",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "WOD not found - The ID does not exist",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires COACH or ADMIN role",
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
                      name = "Update Example",
                      value =
                          """
          {
            "title": "Fran (Scaled)", "wodType": "FOR_TIME", "scoreType": "TIME", "isPublic": true,
            "movements": [ { "movementId": "WL-TR-001", "orderIndex": 1, "weight": 30.0, "weightUnit": "KG" } ]
          }""")))
  public ResponseEntity<WodResponse> updateWod(
      @PathVariable Long id,
      @Valid @RequestBody WodRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {
    return ResponseEntity.ok(wodService.updateWod(id, request));
  }

  /**
   * Deletes a WOD definition.
   *
   * @param id The ID of the WOD to delete.
   * @param principal The authenticated user.
   * @return HTTP 204 No Content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@wodSecurity.canDelete(#id, principal)")
  @Operation(
      summary = "Delete WOD",
      description = "Removes a workout definition (Admin/Coach only).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "WOD deleted successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "WOD not found - The ID does not exist",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Requires COACH or ADMIN role",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - User is not authenticated",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> deleteWod(
      @PathVariable Long id, @AuthenticationPrincipal AldebaranUserPrincipal principal) {
    wodService.deleteWod(id);
    return ResponseEntity.noContent().build();
  }
}
