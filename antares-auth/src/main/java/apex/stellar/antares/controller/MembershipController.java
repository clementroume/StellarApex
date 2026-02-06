package apex.stellar.antares.controller;

import apex.stellar.antares.dto.MembershipResponse;
import apex.stellar.antares.dto.MembershipUpdateRequest;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.MembershipRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing gym memberships.
 *
 * <p>Provides endpoints for listing, updating, and deleting memberships within a gym context.
 */
@RestController
@RequestMapping("/antares/memberships")
@RequiredArgsConstructor
@Tag(name = "Membership Management", description = "Manage members within a gym")
public class MembershipController {

  private final MembershipService membershipService;
  private final MembershipRepository membershipRepository;

  /**
   * Retrieves a list of memberships for a specific gym, optionally filtered by status.
   *
   * @param gymId The ID of the gym.
   * @param status The optional status to filter by.
   * @return A list of membership responses.
   */
  @GetMapping
  @PreAuthorize("@security.hasGymPermission(#gymId, 'MANAGE_MEMBERSHIPS')")
  @Operation(summary = "List Members", description = "Requires MANAGE_MEMBERSHIPS permission.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Memberships retrieved"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Insufficient permissions",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<List<MembershipResponse>> getMemberships(
      @RequestParam Long gymId, @RequestParam(required = false) MembershipStatus status) {
    return ResponseEntity.ok(membershipService.getMemberships(gymId, status));
  }

  /**
   * Updates an existing membership's details such as role, status, or permissions.
   *
   * @param id The ID of the membership to update.
   * @param request The update request containing new details.
   * @param user The currently authenticated user performing the update.
   * @return The updated membership response.
   */
  @PutMapping("/{id}")
  @PreAuthorize("@security.canManageMembership(#id, 'MANAGE_MEMBERSHIPS')")
  @Operation(summary = "Update Membership", description = "Update role, status, permissions.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Membership updated successfully"),
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
            description = "Membership not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<MembershipResponse> updateMembership(
      @PathVariable Long id,
      @Valid @RequestBody MembershipUpdateRequest request,
      @AuthenticationPrincipal User user) {

    // Resolve requester's membership context to enforce hierarchical logic in service
    Membership target = membershipRepository.findById(id).orElseThrow();
    Membership requesterMembership =
        membershipRepository
            .findByUserIdAndGymId(user.getId(), target.getGym().getId())
            .orElse(null);

    return ResponseEntity.ok(
        membershipService.updateMembership(id, request, user, requesterMembership));
  }

  /**
   * Deletes a membership by its ID.
   *
   * @param id The ID of the membership to delete.
   * @return A response indicating the result of the operation.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@security.canManageMembership(#id, 'MANAGE_MEMBERSHIPS')")
  @Operation(summary = "Delete Membership", description = "Remove a member from a gym.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Membership deleted successfully"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Insufficient permissions",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "Membership not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> deleteMembership(@PathVariable Long id) {
    membershipService.deleteMembership(id);
    return ResponseEntity.noContent().build();
  }
}
