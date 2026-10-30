package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Team of a team WOD score: its members share one performance, each with their own score row
 * ({@code scores.team_id}) so the score reaches their history, journal and records.
 */
@Entity
@Table(name = "score_teams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreTeam {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "wod_id", nullable = false)
  private Long wodId;

  /** Box where the team did the WOD; every member belongs to it. */
  @Column(name = "gym_id", nullable = false)
  private Long gymId;

  /** Member who logged the team score. */
  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(name = "created_at", nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  /** Members of the team (the one who logged included). */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "score_team_members", joinColumns = @JoinColumn(name = "team_id"))
  @Column(name = "user_id")
  @BatchSize(size = 50)
  @Builder.Default
  private Set<Long> memberIds = new LinkedHashSet<>();
}
