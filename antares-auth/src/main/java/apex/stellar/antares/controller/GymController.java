package apex.stellar.antares.controller;

import apex.stellar.antares.dto.*;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.User;
import apex.stellar.antares.service.GymService;
import apex.stellar.antares.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing Gym entities.
 *
 * <p>This controller handles gym creation, status updates (admin approval), membership enrollment
 * via codes, and gym settings management.
 */
@RestController
@RequestMapping("/antares/gyms")
@RequiredArgsConstructor
@Tag(name = "Gym Management", description = "Operations related to Gyms and their settings")
public class GymController {

  private final GymService gymService;
  private final MembershipService membershipService;

  /**
   * Creates a new gym.
   *
   * <p>This endpoint requires a valid creation token to prevent spam. The creator is automatically
   * assigned the OWNER role.
   *
   * @param request The gym creation details (name, programming type, etc.).
   * @param user The authenticated user initiating the creation.
   * @return The created gym details with HTTP 201 Created.
   */
  @PostMapping
  @Operation(summary = "Create a Gym", description = "Requires a valid server-side creation token.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Gym created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Invalid creation token",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Gym name already exists",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<GymResponse> createGym(
      @Valid @RequestBody GymRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.status(HttpStatus.CREATED).body(gymService.createGym(request, user));
  }

  /**
   * Retrieves a list of gyms.
   *
   * <p>Visibility depends on the user's role:
   *
   * <ul>
   *   <li><b>ADMIN:</b> Can filter by status (e.g., see PENDING_APPROVAL gyms).
   *   <li><b>OTHERS:</b> Can only see ACTIVE gyms, regardless of the requested status filter.
   * </ul>
   *
   * @param status Optional filter for gym status (active only for non-admins).
   * @param user The authenticated user.
   * @return A list of gym summaries.
   */
  @GetMapping
  @Operation(
      summary = "List Gyms",
      description = "Admins can filter by status; others see ACTIVE only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "List of gyms retrieved"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<GymResponse>> getGyms(
      @RequestParam(required = false) GymStatus status, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(gymService.getAllGyms(user.getPlatformRole(), status));
  }

  /**
   * Updates the status of a gym (e.g., to approve or reject it).
   *
   * <p>This action is strictly reserved for platform administrators.
   *
   * @param id The ID of the gym to update.
   * @param status The new status to apply.
   * @return The updated gym details.
   */
  @PutMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Update Gym Status",
      description = "Platform Admin only (e.g., Approve/Reject).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin only",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Gym not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<GymResponse> updateStatus(
      @PathVariable Long id, @RequestParam GymStatus status) {
    return ResponseEntity.ok(gymService.updateStatus(id, status));
  }

  /**
   * Enrolls the current user in a gym using an enrollment code.
   *
   * @param request The join request containing the gym ID and enrollment code.
   * @param user The authenticated user.
   * @return The newly created membership details.
   */
  @PostMapping("/join")
  @Operation(summary = "Join a Gym", description = "Enrolls the user using a specific code.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Joined gym successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Invalid enrollment code",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Gym not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Already a member",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<MembershipResponse> joinGym(
      @Valid @RequestBody JoinGymRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(membershipService.joinGym(request, user));
  }

  /**
   * Retrieves the sensitive settings of a gym (enrollment code, auto-subscription).
   *
   * <p>Access is granted to Global Admins and users with the {@code MANAGE_SETTINGS} permission for
   * this specific gym (typically Owners).
   *
   * @param gymId The ID of the gym.
   * @return The gym settings.
   */
  @GetMapping("/{gymId}/settings")
  @PreAuthorize("@sec.hasGymPermission(#gymId, 'MANAGE_SETTINGS')")
  @Operation(summary = "Get Gym Settings", description = "Requires MANAGE_SETTINGS permission.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Settings retrieved"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Insufficient permissions",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Gym not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<GymSettingsRequest> getSettings(@PathVariable Long gymId) {
    return ResponseEntity.ok(gymService.getSettings(gymId));
  }

  /**
   * Updates the settings of a gym.
   *
   * <p>Access is granted to Global Admins and users with the {@code MANAGE_SETTINGS} permission.
   *
   * @param gymId The ID of the gym.
   * @param request The new settings to apply.
   * @return HTTP 200 OK on success.
   */
  @PutMapping("/{gymId}/settings")
  @PreAuthorize("@sec.hasGymPermission(#gymId, 'MANAGE_SETTINGS')")
  @Operation(summary = "Update Gym Settings", description = "Requires MANAGE_SETTINGS permission.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Settings updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Insufficient permissions",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Gym not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> updateSettings(
      @PathVariable Long gymId, @Valid @RequestBody GymSettingsRequest request) {
    gymService.updateSettings(gymId, request);
    return ResponseEntity.ok().build();
  }

  /**
   * Deletes a gym permanently.
   *
   * <p>Access is granted to Global Admins and users with the {@code MANAGE_SETTINGS} permission.
   *
   * @param id The ID of the gym to delete.
   * @return HTTP 204 No Content on success.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@sec.hasGymPermission(#id, 'MANAGE_SETTINGS')")
  @Operation(summary = "Delete Gym", description = "Requires MANAGE_SETTINGS permission.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Gym deleted successfully"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Insufficient permissions",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Gym not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> deleteGym(@PathVariable Long id) {
    gymService.deleteGym(id);
    return ResponseEntity.noContent().build();
  }
}
