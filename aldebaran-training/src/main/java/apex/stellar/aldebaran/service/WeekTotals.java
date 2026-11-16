package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The vector of some workouts (a week of an athlete, D120, or a session of a programming, lot D'):
 * read-only outside the service; built by {@link BalanceCalculator}. Linear: the vector of several
 * weeks or sessions is the sum of theirs ({@link BalanceCalculator#sum}).
 */
public final class WeekTotals {

  /** Sets of one muscle: moving (agonist 1, synergist 0.5) and holding (stabilizer 1). */
  public static final class MuscleSets {
    private double moving;
    private double holding;

    public double moving() {
      return moving;
    }

    public double holding() {
      return holding;
    }

    void add(double movingSets, double holdingSets) {
      moving += movingSets;
      holding += holdingSets;
    }
  }

  /** Strength sets of one lift and whether one was near the record. */
  public static final class LiftSets {
    private final @Nullable Long movementId;
    private double sets;
    private boolean nearRecord;

    LiftSets(@Nullable Long movementId) {
      this.movementId = movementId;
    }

    public @Nullable Long movementId() {
      return movementId;
    }

    public double sets() {
      return sets;
    }

    public boolean nearRecord() {
      return nearRecord;
    }

    void add(double count, boolean near) {
      sets += count;
      nearRecord |= near;
    }
  }

  private final Map<Long, MuscleSets> muscles = new LinkedHashMap<>();
  private final Map<Modality, Double> modalities = new EnumMap<>(Modality.class);
  private final Map<TimeDomain, Integer> seconds = new EnumMap<>(TimeDomain.class);
  private final Map<String, LiftSets> lifts = new LinkedHashMap<>();
  private double totalSets;

  public Map<Long, MuscleSets> muscles() {
    return muscles;
  }

  public Map<Modality, Double> modalities() {
    return modalities;
  }

  public Map<TimeDomain, Integer> seconds() {
    return seconds;
  }

  public Map<String, LiftSets> lifts() {
    return lifts;
  }

  /**
   * Whether nothing at all was added.
   *
   * @return True for an empty week.
   */
  public boolean isEmpty() {
    return muscles.isEmpty() && lifts.isEmpty() && totalSets() <= 0 && seconds.values().stream().allMatch(s -> s == 0);
  }

  public double totalSets() {
    return totalSets;
  }

  void addMuscle(Long muscleId, double moving, double holding) {
    muscles.computeIfAbsent(muscleId, id -> new MuscleSets()).add(moving, holding);
  }

  void addSets(@Nullable Modality modality, double sets) {
    totalSets += sets;
    if (modality != null) {
      modalities.merge(modality, sets, Double::sum);
    }
  }

  void addSeconds(TimeDomain domain, int value) {
    seconds.merge(domain, value, Integer::sum);
  }

  void addLift(String label, @Nullable Long movementId, double sets, boolean near) {
    lifts.computeIfAbsent(label, l -> new LiftSets(movementId)).add(sets, near);
  }

  /** Adds another vector to this one. */
  void addAll(WeekTotals other) {
    other.muscles.forEach((id, sets) -> addMuscle(id, sets.moving(), sets.holding()));
    other.modalities.forEach((modality, sets) -> modalities.merge(modality, sets, Double::sum));
    other.seconds.forEach(this::addSeconds);
    other.lifts.forEach((label, sets) -> addLift(label, sets.movementId(), sets.sets(), sets.nearRecord()));
    totalSets += other.totalSets;
  }
}
