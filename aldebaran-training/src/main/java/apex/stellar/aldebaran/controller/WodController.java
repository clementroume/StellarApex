package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.WodFacetsResponse;
import apex.stellar.aldebaran.dto.WodReferenceData;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.dto.WodSummaryResponse;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.WodScope;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.service.WodCapabilityService;
import apex.stellar.aldebaran.service.WodService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
  private final WodCapabilityService wodCapabilityService;

  /**
   * Retrieves a list of available WODs with optional filtering and pagination.
   *
   * <p>Returns lightweight {@link WodSummaryResponse} objects containing only essential metadata to
   * optimize bandwidth.
   *
   * @param search Optional text to search in WOD titles.
   * @param type Optional filter for WOD type (e.g., AMRAP, FOR TIME).
   * @param movementId Optional filter to find WODs containing a specific movement.
   * @param scope List scope: ALL (default), MINE, GYM or PUBLIC.
   * @param gymId Optional gym filter for the GYM scope (must be one of the user's member gyms).
   * @param authorId Optional author filter: that author's personal WODs (readable ones only).
   * @param pageable Pagination info (page, size, sort). Defaults to 20 items per page.
   * @return A list of WOD summaries matching the criteria.
   */
  @GetMapping
  public ResponseEntity<Slice<WodSummaryResponse>> getWods(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) WodType type,
      @RequestParam(required = false) Long movementId,
      @RequestParam(defaultValue = "ALL") WodScope scope,
      @RequestParam(required = false) Long gymId,
      @RequestParam(required = false) Long authorId,
      @PageableDefault(size = 20) Pageable pageable) {

    return ResponseEntity.ok(
        wodService.getWods(search, type, movementId, scope, gymId, authorId, pageable));
  }

  /**
   * Retrieves the values available to filter the WOD list (types, gyms, authors).
   *
   * <p>Computed with the same security clause as the list, so a user only learns about WODs they
   * can read.
   *
   * @param search Optional text to search in WOD titles.
   * @param scope List scope: ALL (default), MINE, GYM or PUBLIC.
   * @param gymId Optional gym filter for the GYM scope.
   * @param authorId Optional author filter (personal WODs).
   * @return The available filter values.
   */
  @GetMapping("/facets")
  public ResponseEntity<WodFacetsResponse> getFacets(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "ALL") WodScope scope,
      @RequestParam(required = false) Long gymId,
      @RequestParam(required = false) Long authorId) {

    return ResponseEntity.ok(wodService.getFacets(search, scope, gymId, authorId));
  }

  /**
   * Retrieves the detailed definition of a specific WOD.
   *
   * <p>The response includes the full "Recipe", including the list of prescribed movements,
   * repetition schemes, weights, and coaching notes, plus the current user's capabilities.
   *
   * @param id The unique identifier of the WOD.
   * @param principal The authenticated user.
   * @return The detailed WOD definition.
   */
  @GetMapping("/{id}")
  @PreAuthorize("@wodSecurity.canRead(#id, principal)")
  public ResponseEntity<WodResponse> getWod(
      @PathVariable Long id, @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        wodCapabilityService.withCapabilities(wodService.getWodDetail(id), principal));
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
  public ResponseEntity<WodResponse> createWod(
      @Valid @RequestBody WodRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(wodCapabilityService.withCapabilities(wodService.createWod(request), principal));
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
  @PreAuthorize("@wodSecurity.canModify(#id, principal)")
  public ResponseEntity<WodResponse> updateWod(
      @PathVariable Long id,
      @Valid @RequestBody WodRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        wodCapabilityService.withCapabilities(wodService.updateWod(id, request), principal));
  }

  /**
   * Deletes a WOD definition.
   *
   * @param id The ID of the WOD to delete.
   * @return HTTP 204 No Content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@wodSecurity.canModify(#id, principal)")
  public ResponseEntity<Void> deleteWod(@PathVariable Long id) {

    wodService.deleteWod(id);
    return ResponseEntity.noContent().build();
  }

  /** Retrieves reference data (types, units) for UI forms. */
  @GetMapping("/reference-data")
  public ResponseEntity<WodReferenceData> getReferenceData() {

    return ResponseEntity.ok(wodService.getReferenceData());
  }
}
