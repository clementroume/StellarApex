package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.enums.Technique;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single "counts for" rule of the catalog: which variant of a movement a performance also
 * proves. A Muscle Clean proves a Power Clean and a Clean; a Strict Pull-up proves a Kipping
 * Pull-up; a Press proves a Push Press. Every value not listed here is a separate record.
 */
public final class VariantDominance {

  /** Values a technique directly counts for. */
  private static final Map<Technique, Set<Technique>> COUNTS_FOR = new EnumMap<>(Technique.class);

  /** Techniques whose absence is enough to count for them: a strict press proves a push press. */
  private static final Set<Technique> RECEIVED_FROM_ABSENT = EnumSet.of(Technique.PUSH);

  /** Techniques that a line can drop and still prove the rest. */
  private static final Set<Technique> COUNTS_FOR_ABSENT =
      EnumSet.of(
          Technique.POWER,
          Technique.SQUAT,
          Technique.SPLIT,
          Technique.DEFICIT,
          Technique.PAUSED,
          Technique.TEMPO,
          Technique.TOUCH_AND_GO,
          Technique.DEAD_STOP,
          Technique.KIPPING,
          Technique.BUTTERFLY,
          Technique.HAND_RELEASE,
          Technique.LEGLESS,
          Technique.CONVENTIONAL,
          Technique.SUMO,
          Technique.CLOSE_STANCE,
          Technique.HIGH_BAR,
          Technique.LOW_BAR,
          Technique.ALTERNATING,
          Technique.CHEST_TO_BAR,
          Technique.CHEST_TO_WALL,
          Technique.FREESTANDING);

  static {
    COUNTS_FOR.put(Technique.MUSCLE, EnumSet.of(Technique.POWER));
    COUNTS_FOR.put(Technique.STRICT, EnumSet.of(Technique.KIPPING, Technique.BUTTERFLY));
    COUNTS_FOR.put(Technique.TOES_TO_BAR, EnumSet.of(Technique.KNEES_TO_ELBOWS));
    COUNTS_FOR.put(Technique.KNEES_TO_ELBOWS, EnumSet.of(Technique.KNEE_RAISE));
  }

  private VariantDominance() {}

  /**
   * Everything a technique counts for, directly or through another value.
   *
   * @param technique A technique.
   * @return The values it proves (never itself).
   */
  public static Set<Technique> closure(Technique technique) {
    Set<Technique> seen = EnumSet.noneOf(Technique.class);
    List<Technique> todo = new ArrayList<>(COUNTS_FOR.getOrDefault(technique, Set.of()));
    while (!todo.isEmpty()) {
      Technique next = todo.removeLast();
      if (seen.add(next)) {
        todo.addAll(COUNTS_FOR.getOrDefault(next, Set.of()));
      }
    }
    return seen;
  }

  /**
   * Whether a technique can be dropped, directly or through another value.
   *
   * @param technique A technique.
   * @return {@code true} if a line with it also counts for the line without it.
   */
  public static boolean countsForAbsent(Technique technique) {
    if (COUNTS_FOR_ABSENT.contains(technique)) {
      return true;
    }
    return closure(technique).stream().anyMatch(COUNTS_FOR_ABSENT::contains);
  }

  /**
   * Whether a variant counts for another variant of the same movement and equipment.
   *
   * @param from The techniques of the performance.
   * @param to The techniques of the variant to prove.
   * @return {@code true} if a performance {@code from} is also a performance {@code to}.
   */
  public static boolean countsFor(Collection<Technique> from, Collection<Technique> to) {
    Set<Technique> left = new HashSet<>(from);
    left.removeAll(to);
    for (Technique wanted : to) {
      if (from.contains(wanted)) {
        continue;
      }
      Technique source =
          left.stream().filter(s -> closure(s).contains(wanted)).findFirst().orElse(null);
      if (source != null) {
        left.remove(source);
      } else if (!RECEIVED_FROM_ABSENT.contains(wanted)) {
        return false;
      }
    }
    return left.stream().allMatch(VariantDominance::countsForAbsent);
  }

  /**
   * Every technique set a performance also proves, itself included.
   *
   * @param from The techniques of the performance.
   * @param allowed The techniques the movement allows.
   * @return The variants proved, limited to what the movement allows.
   */
  public static Set<Set<Technique>> targets(
      Collection<Technique> from, Collection<Technique> allowed) {
    Set<Set<Technique>> results = new HashSet<>();
    results.add(Set.of());
    for (Technique value : from) {
      Set<Set<Technique>> next = new HashSet<>();
      List<Technique> options = new ArrayList<>(closure(value));
      options.add(value);
      for (Set<Technique> partial : results) {
        for (Technique option : options) {
          if (allowed.contains(option) || option == value) {
            Set<Technique> grown = new HashSet<>(partial);
            grown.add(option);
            next.add(grown);
          }
        }
        if (countsForAbsent(value)) {
          next.add(partial);
        }
      }
      results = next;
    }
    Set<Set<Technique>> withReceived = new HashSet<>(results);
    for (Technique received : RECEIVED_FROM_ABSENT) {
      if (!allowed.contains(received) || from.contains(received)) {
        continue;
      }
      for (Set<Technique> variant : results) {
        Set<Technique> grown = new HashSet<>(variant);
        grown.add(received);
        withReceived.add(grown);
      }
    }
    return withReceived;
  }
}
