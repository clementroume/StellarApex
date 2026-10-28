package apex.stellar.antares.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serial;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Represents a registered user in the system.
 *
 * <p>This entity is the core identity provider, implementing Spring Security's {@link UserDetails}.
 * Equality is determined by the unique business key: {@code email}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  /** The unique business key for the user. */
  @Column(unique = true, nullable = false)
  private String email;

  @Column(nullable = false)
  private String password;

  /** Global platform role (ADMIN or USER). */
  @Enumerated(EnumType.STRING)
  @Column(name = "platform_role", nullable = false, length = 50)
  private PlatformRole platformRole;

  @Builder.Default
  @Column(nullable = false)
  private Boolean enabled = true;

  @Builder.Default
  @Column(nullable = false, length = 10)
  private String locale = "en";

  @Builder.Default
  @Column(nullable = false, length = 20)
  private String theme = "light";

  /** UI colour palette (ocean, chalk, volt, rig), combined with the light/dark theme. */
  @Builder.Default
  @Column(nullable = false, length = 20)
  private String palette = "ocean";

  /** Display unit for loads: METRIC (kg) or IMPERIAL (lb); null follows the language. */
  @Column(name = "unit_system", length = 10)
  private String unitSystem;

  /** Whether the user's name may be shown on public leaderboards (gym mates always see it). */
  @Builder.Default
  @Column(name = "public_leaderboard_name", nullable = false)
  private boolean publicLeaderboardName = false;

  /** Whether the user confirmed the current email address through a mailed link. */
  @Builder.Default
  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = false;

  /** Last password change (reset or change); access tokens issued before it are refused. */
  @Column(name = "password_changed_at")
  private Instant passwordChangedAt;

  // --- Athlete profile (all optional) ---

  /** Competition division (men/women loads, separate leaderboards); null when not chosen. */
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Division division;

  /** Birth year, for age divisions; private. */
  @Column(name = "birth_year")
  private Integer birthYear;

  /** Height in centimetres; private. */
  @Column(name = "height_cm")
  private Integer heightCm;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<Membership> memberships = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // --- Spring Security Implementation ---

  @Override
  @NonNull
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + platformRole.name()));
  }

  @Override
  @NonNull
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  // --- Effective Java: Equals & HashCode based on Business Key (Email) ---

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    User user = (User) o;
    return Objects.equals(email, user.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(email);
  }

  @Override
  public String toString() {
    return "User{"
        + "id="
        + id
        + ", email='"
        + email
        + '\''
        + ", platformRole="
        + platformRole
        + ", enabled="
        + enabled
        + '}';
  }
}
