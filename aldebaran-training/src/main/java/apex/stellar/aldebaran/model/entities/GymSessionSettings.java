package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Session settings of a gym (D30): the default visibility policy of its sessions and the time zone
 * used to place "the day of the session" and "the next day" in time. A gym without a row uses the
 * defaults of {@code SessionSettingsService}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gym_session_settings")
public class GymSessionSettings {

  @Id
  @Column(name = "gym_id")
  private Long gymId;

  @Enumerated(EnumType.STRING)
  @Column(name = "default_visibility", nullable = false, length = 30)
  private VisibilityPolicy defaultVisibility;

  @Column(name = "time_zone", nullable = false, length = 50)
  private String timeZone;
}
