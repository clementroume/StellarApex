package apex.stellar.antares.controller;

import apex.stellar.antares.dto.DisplayNameResponse;
import apex.stellar.antares.dto.GymPublicResponse;
import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.GymResponse;
import apex.stellar.antares.dto.GymSettingsRequest;
import apex.stellar.antares.dto.GymSettingsResponse;
import apex.stellar.antares.dto.GymUpdateRequest;
import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipSummary;
import apex.stellar.antares.dto.TransferOwnershipRequest;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymType;
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
 * REST Controller for managing Gym entities.
 *
 * <p>This controller handles gym creation requests, discovery, status updates (admin approval),
 * joining with an enrollment code, leaving, and gym settings management.
 */
@RestController
@RequestMapping("/antares/gyms")
@RequiredArgsConstructor
@Tag(name = "Gym Management", description = "Operations related to Gyms and their settings")
public class GymController {

  private final GymService gymService;
  private final MembershipService membershipService;

  /**
   * Requests the creation of a gym.
   *
   * <p>Open to every user: the gym starts {@code PENDING_APPROVAL} until a platform admin approves
   * it, and the requester becomes its OWNER (box) or PROGRAMMER (program). At most one request may
   * wait for approval per user.
   *
   * @param request The gym creation details (name, programming type, etc.).
   * @param user The authenticated user initiating the creation.
   * @return The created gym details with HTTP 201 Created.
   */
  @PostMapping
  @Operation(
      summary = "Request a Gym",
      description = "Any user; PENDING_APPROVAL until a platform admin approves it.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Gym created successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Gym name already exists, or a request already waits for approval",
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
   * Searches the ACTIVE gyms a user may join (discovery), by name and type.
   *
   * @param search text matched against the name (case-insensitive); blank lists every gym.
   * @param type optional type: {@code BOX} or {@code PROGRAM}.
   * @param pageable page and size (default 20, capped at 50).
   * @return a slice of public gym views (never the enrollment code), ordered by name.
   */
  @GetMapping
  @Operation(
      summary = "Search Gyms",
      description = "ACTIVE boxes and programs by name and type, public view only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Matching gyms"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Slice<GymPublicResponse>> searchGyms(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) GymType type,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(gymService.searchActiveGyms(search, type, pageable));
  }

  /**
   * Returns the public view of an ACTIVE gym, e.g. to show the gym of an invitation link.
   *
   * @param id The gym ID.
   * @return the public gym view.
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get a Gym", description = "Public view of an ACTIVE box or program.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Gym found"),
        @ApiResponse(
            responseCode = "404",
            description = "Gym not found or not active",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<GymPublicResponse> getGym(@PathVariable Long id) {
    return ResponseEntity.ok(gymService.getActiveGym(id));
  }

  /**
   * Renames a gym or changes its description.
   *
   * @param gymId The gym ID.
   * @param request The new name and description.
   * @return the updated settings view.
   */
  @PutMapping("/{gymId}")
  @PreAuthorize("@security.hasGymPermission(#gymId, 'MANAGE_SETTINGS')")
  @Operation(summary = "Update a Gym", description = "Requires MANAGE_SETTINGS permission.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Gym updated"),
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
            responseCode = "409",
            description = "Gym name already exists",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<GymSettingsResponse> updateGym(
      @PathVariable Long gymId, @Valid @RequestBody GymUpdateRequest request) {
    return ResponseEntity.ok(gymService.updateGym(gymId, request));
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
   * @return The membership (PENDING, or ACTIVE with auto-subscription).
   */
  @PostMapping("/join")
  @Operation(
      summary = "Join a Gym",
      description =
          "Enrolls the user using the enrollment code; a former member (INACTIVE) is reactivated.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Joined gym successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error or invalid enrollment code",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Gym not found or not active",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Already a member, or banned",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<MembershipSummary> joinGym(
      @Valid @RequestBody JoinGymRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(membershipService.joinGym(request, user));
  }

  /**
   * Leaves a gym: the membership becomes INACTIVE (history and scores kept), a pending request is
   * withdrawn.
   *
   * @param gymId The gym to leave.
   * @param user The authenticated member.
   * @return HTTP 204 No Content.
   */
  @DeleteMapping("/{gymId}/membership/me")
  @Operation(
      summary = "Leave a Gym",
      description = "Self-service; the owner/programmer must transfer the gym first.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Gym left"),
        @ApiResponse(
            responseCode = "404",
            description = "Not a member of this gym",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Owner/programmer of the gym, or banned",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> leaveGym(
      @PathVariable Long gymId, @AuthenticationPrincipal User user) {
    membershipService.leaveGym(gymId, user);
    return ResponseEntity.noContent().build();
  }

  /**
   * Retrieves the settings of a gym (name, description, status, enrollment code,
   * auto-subscription).
   *
   * <p>Access is granted to Global Admins and users with the {@code MANAGE_SETTINGS} permission for
   * this specific gym (typically Owners).
   *
   * @param gymId The ID of the gym.
   * @return The gym settings.
   */
  @GetMapping("/{gymId}/settings")
  @PreAuthorize("@security.hasGymPermission(#gymId, 'MANAGE_SETTINGS')")
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
  public ResponseEntity<GymSettingsResponse> getSettings(@PathVariable Long gymId) {
    return ResponseEntity.ok(gymService.getSettings(gymId));
  }

  /**
   * Lists the usable members of a gym with their display name, for the staff logging competition
   * scores on their behalf.
   *
   * <p>Access is granted to Global Admins and users with the {@code SCORE_VERIFY} permission for
   * this gym.
   *
   * @param gymId The ID of the gym.
   * @return The members (ID and "First L." name), sorted by name.
   */
  @GetMapping("/{gymId}/athletes")
  @PreAuthorize("@security.hasGymPermission(#gymId, 'SCORE_VERIFY')")
  @Operation(
      summary = "List gym athletes",
      description =
          "Usable members of the gym with their display name. Requires SCORE_VERIFY permission.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Members retrieved"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Insufficient permissions",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<DisplayNameResponse>> getAthletes(@PathVariable Long gymId) {
    return ResponseEntity.ok(gymService.getAthletes(gymId));
  }

  /**
   * Updates the settings of a gym.
   *
   * <p>Access is granted to Global Admins and users with the {@code MANAGE_SETTINGS} permission.
   *
   * @param gymId The ID of the gym.
   * @param request The new settings to apply.
   * @return The updated settings.
   */
  @PutMapping("/{gymId}/settings")
  @PreAuthorize("@security.hasGymPermission(#gymId, 'MANAGE_SETTINGS')")
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
  public ResponseEntity<GymSettingsResponse> updateSettings(
      @PathVariable Long gymId, @Valid @RequestBody GymSettingsRequest request) {
    return ResponseEntity.ok(gymService.updateSettings(gymId, request));
  }

  /**
   * Replaces the enrollment code with a new random one; former invitation links stop working.
   *
   * @param gymId The ID of the gym.
   * @return The updated settings.
   */
  @PostMapping("/{gymId}/settings/enrollment-code")
  @PreAuthorize("@security.hasGymPermission(#gymId, 'MANAGE_SETTINGS')")
  @Operation(
      summary = "Regenerate the enrollment code",
      description = "Requires MANAGE_SETTINGS permission.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "New code generated"),
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
  public ResponseEntity<GymSettingsResponse> regenerateEnrollmentCode(@PathVariable Long gymId) {
    return ResponseEntity.ok(gymService.regenerateEnrollmentCode(gymId));
  }

  /**
   * Transfers the gym holder role to another active member.
   *
   * <p>The target becomes OWNER (physical gym) or PROGRAMMER (virtual gym); the former holder
   * becomes COACH (physical) or ATHLETE (virtual), without explicit permissions. This is the only
   * way to change who holds a gym.
   *
   * @param gymId The ID of the gym.
   * @param request The membership of the new holder.
   * @return The updated memberships of both members.
   */
  @PostMapping("/{gymId}/transfer-ownership")
  @PreAuthorize("@security.isGymHolder(#gymId)")
  @Operation(
      summary = "Transfer Gym Ownership",
      description = "Current OWNER/PROGRAMMER of the gym or Platform Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Ownership transferred"),
        @ApiResponse(
            responseCode = "400",
            description = "Target is not an active member of this gym, or already holds it",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Not the gym holder",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Gym or membership not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<MembershipResponse>> transferOwnership(
      @PathVariable Long gymId, @Valid @RequestBody TransferOwnershipRequest request) {
    return ResponseEntity.ok(membershipService.transferOwnership(gymId, request.membershipId()));
  }

  /**
   * Deletes a gym permanently, with all its memberships (also how a holder withdraws a rejected
   * creation request).
   *
   * <p>Like an ownership transfer, reserved to the gym holder (OWNER/PROGRAMMER) and Global Admins:
   * a coach holding {@code MANAGE_SETTINGS} cannot delete the gym.
   *
   * @param id The ID of the gym to delete.
   * @return HTTP 204 No Content on success.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@security.isGymHolder(#id)")
  @Operation(
      summary = "Delete Gym",
      description = "Current OWNER/PROGRAMMER of the gym or Platform Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Gym deleted successfully"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Not the gym holder",
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
