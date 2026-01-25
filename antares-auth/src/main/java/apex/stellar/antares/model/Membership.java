package apex.stellar.antares.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents the contractual relationship between a {@link User} and a {@link Gym}.
 *
 * <p>This entity serves as the authorization context. Equality is based on the combination of
 * {@code user} and {@code gym}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "memberships",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "gym_id"})})
@EntityListeners(AuditingEntityListener.class)
public class Membership implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The user holding this membership. Fetch type is LAZY for performance. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** The gym to which the user belongs. Fetch type is LAZY for performance. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "gym_id", nullable = false)
  private Gym gym;

  /** The current status of the membership (e.g., PENDING validation, ACTIVE). */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private MembershipStatus status = MembershipStatus.PENDING;

  /** The role assigned to the user within this specific gym context. */
  @Enumerated(EnumType.STRING)
  @Column(name = "gym_role", nullable = false, length = 50)
  private GymRole gymRole;

  /**
   * A set of granular permissions granted to the member. These are additive to the permissions
   * implied by the {@code role}.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "membership_permissions",
      joinColumns = @JoinColumn(name = "membership_id"))
  @Column(name = "permission", nullable = false)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Set<Permission> permissions = new HashSet<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // --- Helper Methods (Avoid Lazy Loading triggers) ---

  public Long getGymId() {
    return gym != null ? gym.getId() : null;
  }

  public Long getUserId() {
    return user != null ? user.getId() : null;
  }

  // --- Effective Java: Equals & HashCode based on Business Key (User + Gym) ---

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Membership that = (Membership) o;
    return Objects.equals(getUserId(), that.getUserId())
        && Objects.equals(getGymId(), that.getGymId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getUserId(), getGymId());
  }

  @Override
  public String toString() {
    return "Membership{"
        + "id="
        + id
        + ", userId="
        + getUserId()
        + ", gymId="
        + getGymId()
        + ", status="
        + status
        + ", gymRole="
        + gymRole
        + '}';
  }

  /** Enumeration representing the state of a user's membership. */
  public enum MembershipStatus {
    PENDING,
    ACTIVE,
    INACTIVE,
    BANNED
  }
}
