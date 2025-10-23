package com.antares.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Represents a user in the system with authentication and authorization details.
 *
 * <p>This entity is mapped to the "users" table in the database and implements the UserDetails
 * interface from Spring Security to provide necessary user information for authentication and
 * authorization processes.
 *
 * <ul>
 *   <li>id: Unique identifier for the user.
 *   <li>firstName: The user's first name.
 *   <li>lastName: The user's last name.
 *   <li>email: The user's email address, used as the username for authentication.
 *   <li>password: The user's password, stored in an encoded format.
 *   <li>role: The role assigned to the user, determining their access level.
 *   <li>enabled: Indicates whether the user's account is active.
 *   <li>locale: The user's preferred locale for localization purposes.
 *   <li>theme: The user's preferred theme for UI customization.
 *   <li>createdAt: Timestamp of when the user was created.
 *   <li>updatedAt: Timestamp of when the user was last updated.
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User implements UserDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(unique = true, nullable = false)
  private String email;

  @Column(nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private Role role;

  @Builder.Default
  @Column(nullable = false)
  private Boolean enabled = true;

  @Builder.Default
  @Column(nullable = false, length = 10)
  private String locale = "en";

  @Builder.Default
  @Column(nullable = false, length = 20)
  private String theme = "light";

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(role.name()));
  }

  @Override
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

  /**
   * Automatically sets the creation and last updated timestamps before persisting the entity.
   *
   * <p>This method is annotated with {@code @PrePersist} and is executed by the JPA provider just
   * before the entity is inserted into the database.
   *
   * <p>It initializes the {@code createdAt} and {@code updatedAt} fields to the current timestamp
   * using {@link LocalDateTime#now()} to ensure consistent tracking of entity lifecycle events.
   */
  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    User user = (User) o;
    return id != null && Objects.equals(id, user.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
