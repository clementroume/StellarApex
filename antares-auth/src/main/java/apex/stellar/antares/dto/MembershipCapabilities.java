package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the current user may do with a membership of their gym, computed by Antares from the rules
 * enforced by {@code MembershipService} so that clients never re-implement them.
 *
 * @param canChangeStatus Validate a request, set the member INACTIVE or ACTIVE again.
 * @param canChangeRole Switch between COACH and ATHLETE (physical box only).
 * @param canChangePermissions Grant or revoke coach permissions (physical box only).
 * @param canRemove Refuse a pending request (deleted) or remove a member (INACTIVE).
 * @param canBan Ban the member or lift a ban.
 * @param canBecomeHolder Receive the gym through an ownership transfer.
 */
@Schema(description = "Actions the current user may perform on this membership.")
public record MembershipCapabilities(
    boolean canChangeStatus,
    boolean canChangeRole,
    boolean canChangePermissions,
    boolean canRemove,
    boolean canBan,
    boolean canBecomeHolder) {

  /** No action allowed. */
  public static final MembershipCapabilities NONE =
      new MembershipCapabilities(false, false, false, false, false, false);
}
