package com.antares.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * Represents a user in the system.
 *
 * <p>This entity is mapped to the "users" table in the database and implements the Spring Security
 * {@link UserDetails} interface to integrate with the authentication and authorization processes.
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

  /**
   * Returns the authorities granted to the user.
   *
   * @return A collection containing the user's role.
   */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(role.name()));
  }

  /**
   * Returns the email address used to authenticate the user.
   *
   * @return The user's email.
   */
  @Override
  public String getUsername() {
    return email;
  }

  /**
   * Returns the user's hashed password.
   *
   * @return The hashed password.
   */
  @Override
  public String getPassword() {
    return password;
  }

  /**
   * Indicates whether the user's account is enabled.
   *
   * @return true if the user is enabled, false otherwise.
   */
  @Override
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * JPA lifecycle callback to set creation and update timestamps automatically before a new entity
   * is persisted.
   */
  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * JPA lifecycle callback to update the 'updatedAt' timestamp automatically before an existing
   * entity is updated.
   */
  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  /** Checks equality based on the entity's ID. */
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

  /** Generates a hash code based on the class, ensuring consistency for entity lifecycle. */
  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
