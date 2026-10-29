package apex.stellar.aldebaran.model.entities;

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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Journal of a session done by an athlete ("Séance faite", D13): the blocks done, and the scores
 * linked through {@code scores.session_log_id}. Its existence makes the athlete a participant.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "session_logs")
@EntityListeners(AuditingEntityListener.class)
public class SessionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  @ToString.Exclude
  private Session session;

  /** Gym where the session was done (the session's gym, or one of the athlete's for a personal). */
  @Column(name = "gym_id")
  private Long gymId;

  /** Day the athlete did the session. */
  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  @Builder.Default
  private Source source = Source.MANUAL;

  /** User who created the journal: the athlete, or a judge logging a competition score. */
  @Column(name = "logged_by", nullable = false)
  private Long loggedBy;

  /** Done flag of each block of the session, by block ID. */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "session_log_blocks", joinColumns = @JoinColumn(name = "log_id"))
  @MapKeyColumn(name = "block_id")
  @Column(name = "done", nullable = false)
  @BatchSize(size = 50)
  @Builder.Default
  private Map<Long, Boolean> blocks = new HashMap<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return id != null && id.equals(((SessionLog) o).id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  /** Who created the journal: the athlete (or staff), or the planning service at attendance. */
  public enum Source {
    MANUAL,
    PLANNING
  }
}
