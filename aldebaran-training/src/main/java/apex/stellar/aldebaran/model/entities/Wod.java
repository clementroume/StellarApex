package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Represents the definition of a Workout of the Day (WOD). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wods")
@EntityListeners(AuditingEntityListener.class)
public class Wod {

  // --- Identification ---
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(name = "version")
  private Long version;

  @Column(name = "title", nullable = false, length = 100)
  @NotBlank
  @Size(max = 100)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(name = "wod_type", nullable = false, length = 50)
  @NotNull
  private WodType wodType;

  /** Optional family of the WOD (Girls, Hero, Open...); its format stays in {@code wodType}. */
  @Enumerated(EnumType.STRING)
  @Column(name = "category", length = 50)
  private WodCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "score_type", nullable = false, length = 20)
  @NotNull
  private ScoreType scoreType;

  // --- Authorship and Visibility ---
  @Column(name = "author_id")
  private Long authorId;

  @Column(name = "gym_id")
  private Long gymId;

  @Column(name = "is_public", nullable = false)
  @Builder.Default
  private boolean isPublic = false;

  /**
   * Competition WOD (gym WODs only, never public): staff holding SCORE_VERIFY in its gym may log
   * scores for the gym's athletes and verify them.
   */
  @Column(name = "competition", nullable = false)
  @Builder.Default
  private boolean competition = false;

  /**
   * Standalone WOD (listed, scored on its own), or the WOD of one session block (false): hidden from
   * WOD lists, read and edited through its session.
   */
  @Column(name = "standalone", nullable = false)
  @Builder.Default
  private boolean standalone = true;

  /** WOD this one was copied from (e.g. a public Open WOD copied for a gym competition). */
  @Column(name = "source_wod_id")
  private Long sourceWodId;

  // --- Description and Notes (EN / FR; a WOD may have only one language) ---
  @Column(name = "description_en", columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(name = "description_fr", columnDefinition = "TEXT")
  private String descriptionFr;

  @Column(name = "notes_en", columnDefinition = "TEXT")
  private String notesEn;

  @Column(name = "notes_fr", columnDefinition = "TEXT")
  private String notesFr;

  // --- Tiebreak (EN / FR): how the official tiebreak time is taken; none when null ---
  @Column(name = "tiebreak_en", columnDefinition = "TEXT")
  private String tiebreakEn;

  @Column(name = "tiebreak_fr", columnDefinition = "TEXT")
  private String tiebreakFr;

  // --- Structure and Prescription ---
  @Column(name = "time_cap_seconds")
  @Min(0)
  private Integer timeCapSeconds;

  /**
   * Reps of one round of an AMRAP with a fixed round (Cindy: 5 + 10 + 15 = 30), used to show and
   * enter a total-reps score as rounds + reps; null when rounds are not fixed (ladders, chippers).
   */
  @Column(name = "reps_per_round")
  @Min(1)
  private Integer repsPerRound;

  @Column(name = "emom_interval")
  @Min(0)
  private Integer emomInterval;

  /** Sub-category of the format (For Time: Rounds, Scheme, Chipper...), or null. */
  @Enumerated(EnumType.STRING)
  @Column(name = "structure", length = 20)
  private WodStructure structure;

  /** Rounds of a "rounds for time" (Helen: 3), or null. */
  @Column(name = "rounds")
  @Min(1)
  private Integer rounds;

  /** Rest between rounds or intervals, in seconds (Barbara: 180), or null. */
  @Column(name = "rest_seconds")
  @Min(0)
  private Integer restSeconds;

  /** Work time of an interval, in seconds (Tabata: 20), or null. */
  @Column(name = "work_seconds")
  @Min(1)
  private Integer workSeconds;

  /** Ascending ladder (3-6-9...): reps of the first rung, or null. */
  @Column(name = "ladder_start")
  @Min(1)
  private Integer ladderStart;

  /** Ascending ladder: reps added at each rung, or null. */
  @Column(name = "ladder_step")
  @Min(1)
  private Integer ladderStep;

  /** Weight vest worn for the whole WOD (Murph), men's and women's, in {@code vestWeightUnit}. */
  @Column(name = "vest_weight")
  @DecimalMin("0.0")
  private Double vestWeight;

  @Column(name = "vest_weight_female")
  @DecimalMin("0.0")
  private Double vestWeightFemale;

  @Enumerated(EnumType.STRING)
  @Column(name = "vest_weight_unit", length = 10)
  private Unit vestWeightUnit;

  @Column(name = "emom_rounds")
  @Min(0)
  private Integer emomRounds;

  @Column(name = "rep_scheme", length = 100)
  @Size(max = 100)
  private String repScheme;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "wod_modalities", joinColumns = @JoinColumn(name = "wod_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "modality", length = 50, nullable = false)
  @Builder.Default
  private Set<Modality> modalities = new HashSet<>();

  @OneToMany(mappedBy = "wod", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @Builder.Default
  @ToString.Exclude
  private List<WodMovement> movements = new ArrayList<>();

  // --- Audit ---
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // --- Equality and Hashing ---
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Wod wod = (Wod) o;
    return id != null && id.equals(wod.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  /** Format of the WOD (how it is performed and scored). */
  @Getter
  @RequiredArgsConstructor
  public enum WodType {
    FOR_TIME,
    AMRAP,
    EMOM,
    TABATA,
    FOR_LOAD,
    // Skill, accessory or mobility work, without a score
    FOR_QUALITY
  }

  /** Sub-category of a WOD format, guiding its entry ({@link #allowedIn} gives its formats). */
  public enum WodStructure {
    // For Time: lines done once, rounds, shared rep scheme (Fran 21-15-9), chipper, ladder / pyramid
    SINGLE(WodType.FOR_TIME),
    ROUNDS(WodType.FOR_TIME, WodType.AMRAP),
    SCHEME(WodType.FOR_TIME, WodType.FOR_LOAD),
    CHIPPER(WodType.FOR_TIME),
    LADDER(WodType.FOR_TIME, WodType.AMRAP),
    // AMRAP: rounds of max reps without a clock (Lynne)
    MAX_REPS(WodType.AMRAP),
    // EMOM
    EVERY_MINUTE(WodType.EMOM),
    EVERY_X(WodType.EMOM),
    INTERVALS(WodType.EMOM),
    DEATH_BY(WodType.EMOM),
    // Tabata: 8 x 20 s / 10 s, or other work / rest
    CLASSIC(WodType.TABATA),
    CUSTOM(WodType.TABATA),
    // For Load: sets x reps, 1RM / heavy single, weightlifting complex
    SETS(WodType.FOR_LOAD),
    MAX(WodType.FOR_LOAD),
    COMPLEX(WodType.FOR_LOAD),
    // For Quality
    PRACTICE(WodType.FOR_QUALITY);

    private final Set<WodType> formats;

    WodStructure(WodType... formats) {
      this.formats = EnumSet.copyOf(List.of(formats));
    }

    /**
     * Whether this sub-category belongs to the given format.
     *
     * @param type A WOD format.
     * @return {@code true} if allowed.
     */
    public boolean allowedIn(WodType type) {
      return formats.contains(type);
    }
  }

  /** Optional family of the WOD, independent of its format. */
  @Getter
  @RequiredArgsConstructor
  public enum WodCategory {
    // --- Benchmarks ---
    GIRLS,
    HERO,
    BENCHMARK,
    // --- Official competitions ---
    OPEN,
    QUARTERFINALS,
    SEMIFINALS,
    GAMES
  }

  /** ScoreType Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum ScoreType {
    TIME,
    // Rounds completed on the minute (EMOM such as "Chelsea"), capped by emomRounds
    ROUNDS,
    REPS,
    WEIGHT,
    LOAD,
    CALORIES,
    DISTANCE,
    NONE
  }
}
