package apex.stellar.antares.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit record of an admin support session: a platform ADMIN temporarily acting as a real user,
 * for instance to reproduce a bug reported by a gym.
 *
 * <p>Users are referenced by id only (no association) so the audit trail survives account
 * deletion. A session is active while {@code endedAt} is null and {@code expiresAt} is in the
 * future.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "support_sessions")
public class SupportSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The platform admin acting through the session. */
  @Column(name = "admin_id", nullable = false, updatable = false)
  private Long adminId;

  /** The user whose identity is used during the session. */
  @Column(name = "target_user_id", nullable = false, updatable = false)
  private Long targetUserId;

  /** Why the admin needs the session (e.g. the support ticket). */
  @Column(nullable = false, updatable = false)
  private String reason;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  /** When the session was ended (explicitly, by logout or once detected expired). */
  @Column(name = "ended_at")
  private Instant endedAt;

  /**
   * Indicates whether the session can still be used.
   *
   * @param now the current instant.
   * @return {@code true} if not ended and not expired.
   */
  public boolean isActive(Instant now) {
    return endedAt == null && now.isBefore(expiresAt);
  }

  /**
   * Marks the session as ended, if it is not already: at {@code now}, or at its expiry when that
   * came first.
   *
   * @param now the current instant.
   * @return {@code true} if this call ended the session.
   */
  public boolean end(Instant now) {
    if (endedAt != null) {
      return false;
    }
    endedAt = now.isBefore(expiresAt) ? now : expiresAt;
    return true;
  }
}
