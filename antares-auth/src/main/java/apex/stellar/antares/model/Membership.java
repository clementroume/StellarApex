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
 * Represents a user's membership to a specific Gym (Tenant).
 *
 * <p>This entity links a {@link User} to a Gym (identified by ID) and defines their role and
 * permissions within that specific context.
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "gym_id", nullable = false)
  private Long gymId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 50)
  private Role role;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Membership that = (Membership) o;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
