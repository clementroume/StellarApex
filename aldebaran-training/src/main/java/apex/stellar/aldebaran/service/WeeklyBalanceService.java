package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.WeeklyBalanceResponse.LiftBalance;
import apex.stellar.aldebaran.dto.WeeklyBalanceResponse.MuscleBalance;
import apex.stellar.aldebaran.dto.WeeklyBalanceResponse.WeekVolume;
import apex.stellar.aldebaran.dto.WeeklyBalanceResponse;
import apex.stellar.aldebaran.model.entities.BalanceWeek;
import apex.stellar.aldebaran.model.entities.BalanceWeekLift;
import apex.stellar.aldebaran.model.entities.BalanceWeekMuscle;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.model.entities.SessionLog;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import apex.stellar.aldebaran.repository.BalanceWeekLiftRepository;
import apex.stellar.aldebaran.repository.BalanceWeekMuscleRepository;
import apex.stellar.aldebaran.repository.BalanceWeekRepository;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.SessionBlockRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.security.SecurityService;
import apex.stellar.aldebaran.service.BalanceCalculator.NearRecord;
import apex.stellar.aldebaran.service.MovementRecordService.RecordPoint;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The athlete's weekly balance (lot D, D120): stored weeks, rebuilt on first display when a write
 * marked them stale, when the rules changed, or when never computed. Only the current user's own
 * balance: there is no read of someone else's (D89, rights in the backend).
 */
@Service
@RequiredArgsConstructor
public class WeeklyBalanceService {

  /** Weeks of the sections (the chosen week and the 3 before, D125). */
  public static final int WINDOW_WEEKS = 4;

  /** Weeks of the volume histogram (the chosen week and the 7 before, D125). */
  public static final int HISTORY_WEEKS = 8;

  private final BalanceWeekRepository weekRepository;
  private final BalanceWeekMuscleRepository muscleRepository;
  private final BalanceWeekLiftRepository liftRepository;
  private final ScoreRepository scoreRepository;
  private final SessionLogRepository sessionLogRepository;
  private final SessionBlockRepository sessionBlockRepository;
  private final MovementRecordService recordService;
  private final SecurityService securityService;

  /**
   * The current user's balance for a week: muscles, Metcons, Force and Modalities over the week and
   * the 3 before, the volume of the week and the 7 before.
   *
   * @param day Any day of the chosen week (Sirius sends its Monday, D125).
   * @return The balance.
   */
  @Transactional
  public WeeklyBalanceResponse getMyBalance(LocalDate day) {
    Long userId = securityService.getCurrentUserId();
    LocalDate week = monday(day);
    LocalDate historyStart = week.minusWeeks(HISTORY_WEEKS - 1L);
    LocalDate windowStart = week.minusWeeks(WINDOW_WEEKS - 1L);
    refresh(userId, historyStart, week);

    Map<LocalDate, BalanceWeek> stored = new HashMap<>();
    weekRepository.findWeeks(userId, historyStart, week).forEach(w -> stored.put(w.getId().weekStart(), w));
    List<WeekVolume> volume = new ArrayList<>();
    Map<Modality, Double> modalities = new EnumMap<>(Modality.class);
    for (Modality m : Modality.values()) {
      modalities.put(m, 0.0);
    }
    Map<TimeDomain, Integer> seconds = new EnumMap<>(TimeDomain.class);
    for (LocalDate monday = historyStart; !monday.isAfter(week); monday = monday.plusWeeks(1)) {
      BalanceWeek w = stored.get(monday);
      volume.add(new WeekVolume(monday, w == null ? 0 : w.getTotalSets()));
      if (w != null && !monday.isBefore(windowStart)) {
        modalities.merge(Modality.WEIGHTLIFTING, w.getWeightlifting(), Double::sum);
        modalities.merge(Modality.GYMNASTICS, w.getGymnastics(), Double::sum);
        modalities.merge(Modality.MONOSTRUCTURAL, w.getMonostructural(), Double::sum);
        seconds.merge(TimeDomain.VERY_SHORT, w.getVeryShortSeconds(), Integer::sum);
        seconds.merge(TimeDomain.SHORT, w.getShortSeconds(), Integer::sum);
        seconds.merge(TimeDomain.MEDIUM, w.getMediumSeconds(), Integer::sum);
        seconds.merge(TimeDomain.LONG, w.getLongSeconds(), Integer::sum);
        seconds.merge(TimeDomain.INTERVALS, w.getIntervalsSeconds(), Integer::sum);
      }
    }
    Map<TimeDomain, Integer> minutes = new EnumMap<>(TimeDomain.class);
    for (TimeDomain d : TimeDomain.values()) {
      minutes.put(d, Math.round(seconds.getOrDefault(d, 0) / 60f));
    }
    List<MuscleBalance> muscles =
        muscleRepository.sumByMuscle(userId, windowStart, week).stream()
            .map(
                m ->
                    new MuscleBalance(
                        m.getMuscleId(),
                        m.getCode(),
                        m.getRegion().name(),
                        m.getMedicalName(),
                        m.getCommonNameEn(),
                        m.getCommonNameFr(),
                        m.getMovingSets(),
                        m.getHoldingSets()))
            .toList();
    List<LiftBalance> lifts =
        liftRepository.sumByLift(userId, windowStart, week).stream()
            .map(
                l ->
                    new LiftBalance(
                        l.getLabel(), l.getMovementId(), l.getSets(), l.getNearRecord() != null && l.getNearRecord() > 0))
            .toList();
    return new WeeklyBalanceResponse(week, windowStart, muscles, volume, minutes, lifts, modalities);
  }

  /** Rebuilds the weeks of a range that are missing, stale or of older rules. */
  private void refresh(Long userId, LocalDate from, LocalDate to) {
    Map<LocalDate, BalanceWeek> states = new HashMap<>();
    weekRepository.findWeeks(userId, from, to).forEach(w -> states.put(w.getId().weekStart(), w));
    List<LocalDate> todo = new ArrayList<>();
    for (LocalDate monday = from; !monday.isAfter(to); monday = monday.plusWeeks(1)) {
      BalanceWeek state = states.get(monday);
      if (state == null || state.isStale() || state.getRulesVersion() != BalanceCalculator.RULES_VERSION) {
        todo.add(monday);
      }
    }
    if (todo.isEmpty()) {
      return;
    }
    weekRepository.lockAthlete(userId);
    Division division = securityService.getCurrentUserDivision();
    NearRecord records = nearRecord(userId);
    for (LocalDate monday : todo) {
      save(userId, monday, compute(userId, monday, division, records));
    }
  }

  /** Replaces a stored week by its new totals. */
  private void save(Long userId, LocalDate monday, WeekTotals totals) {
    muscleRepository.deleteWeek(userId, monday);
    liftRepository.deleteWeek(userId, monday);
    weekRepository.upsert(
        userId,
        monday,
        BalanceCalculator.RULES_VERSION,
        totals.totalSets(),
        totals.modalities().getOrDefault(Modality.WEIGHTLIFTING, 0.0),
        totals.modalities().getOrDefault(Modality.GYMNASTICS, 0.0),
        totals.modalities().getOrDefault(Modality.MONOSTRUCTURAL, 0.0),
        totals.seconds().getOrDefault(TimeDomain.VERY_SHORT, 0),
        totals.seconds().getOrDefault(TimeDomain.SHORT, 0),
        totals.seconds().getOrDefault(TimeDomain.MEDIUM, 0),
        totals.seconds().getOrDefault(TimeDomain.LONG, 0),
        totals.seconds().getOrDefault(TimeDomain.INTERVALS, 0),
        LocalDateTime.now());
    List<BalanceWeekMuscle> muscles = new ArrayList<>();
    totals
        .muscles()
        .forEach(
            (muscleId, sets) ->
                muscles.add(
                    BalanceWeekMuscle.builder()
                        .id(new BalanceWeekMuscle.Key(userId, monday, muscleId))
                        .movingSets(sets.moving())
                        .holdingSets(sets.holding())
                        .build()));
    muscleRepository.saveAll(muscles);
    List<BalanceWeekLift> lifts = new ArrayList<>();
    totals
        .lifts()
        .forEach(
            (label, sets) ->
                lifts.add(
                    BalanceWeekLift.builder()
                        .id(new BalanceWeekLift.Key(userId, monday, label))
                        .movementId(sets.movementId())
                        .sets(sets.sets())
                        .nearRecord(sets.nearRecord())
                        .build()));
    liftRepository.saveAll(lifts);
  }

  /**
   * Computes one week from the athlete's scores and journals (D82): every score counts; a block
   * checked in a journal without a score of it counts as prescribed, unless the same WOD was scored
   * that day elsewhere (a borrowed WOD).
   */
  private WeekTotals compute(Long userId, LocalDate monday, @Nullable Division division, NearRecord records) {
    LocalDate sunday = monday.plusDays(6);
    List<Score> scores = scoreRepository.findByUserIdAndDateBetween(userId, monday, sunday);
    List<SessionLog> logs = sessionLogRepository.findByUserIdAndDateBetween(userId, monday, sunday);

    Set<Long> blockIds = new HashSet<>();
    scores.stream().map(Score::getSessionBlockId).filter(Objects::nonNull).forEach(blockIds::add);
    logs.forEach(
        log -> log.getBlocks().forEach((id, done) -> {
          if (Boolean.TRUE.equals(done)) {
            blockIds.add(id);
          }
        }));
    Map<Long, SessionBlock> blocks = new HashMap<>();
    sessionBlockRepository.findAllById(blockIds).forEach(b -> blocks.put(b.getId(), b));

    WeekTotals totals = new WeekTotals();
    Set<Long> scoredBlocks = new HashSet<>();
    Set<String> scoredWodDays = new HashSet<>();
    for (Score score : scores) {
      if (score.getSessionBlockId() != null) {
        scoredBlocks.add(score.getSessionBlockId());
      }
      scoredWodDays.add(score.getWod().getId() + "@" + score.getDate());
      SessionBlock block = score.getSessionBlockId() == null ? null : blocks.get(score.getSessionBlockId());
      int members = score.getTeam() == null ? 0 : score.getTeam().getMemberIds().size();
      BalanceCalculator.add(
          new Workout(
              score.getWod(),
              score,
              score.getDate(),
              block == null ? null : block.getKind(),
              score.getDivision() != null ? score.getDivision() : division,
              members),
          records,
          totals);
    }
    for (SessionLog log : logs) {
      for (Map.Entry<Long, Boolean> entry : log.getBlocks().entrySet()) {
        SessionBlock block = blocks.get(entry.getKey());
        if (!Boolean.TRUE.equals(entry.getValue()) || block == null || block.getWod() == null) {
          continue;
        }
        Wod wod = block.getWod();
        if (scoredBlocks.contains(block.getId()) || scoredWodDays.contains(wod.getId() + "@" + log.getDate())) {
          continue;
        }
        BalanceCalculator.add(new Workout(wod, null, log.getDate(), block.getKind(), division, 0), records, totals);
      }
    }
    return totals;
  }

  /**
   * "Near the record" against the best 1RM of the set's day, that day included (D124): the record
   * of the variant as written, else of the base version; a real 1RM or the Epley estimate of a set
   * of 2 to 10 reps.
   */
  private NearRecord nearRecord(Long userId) {
    Map<Long, List<RecordPoint>> histories = new HashMap<>();
    return (Movement movement, String variantKey, LocalDate date, double loadKg) -> {
      List<RecordPoint> history =
          histories.computeIfAbsent(movement.getId(), id -> recordService.history(userId, id));
      Optional<Double> best = bestOneRm(history, variantKey, date);
      if (best.isEmpty() && !LiftVariant.BASE.equals(variantKey)) {
        best = bestOneRm(history, LiftVariant.BASE, date);
      }
      return best.isPresent() && loadKg >= BalanceCalculator.NEAR_RECORD * best.get();
    };
  }

  static Optional<Double> bestOneRm(List<RecordPoint> history, String variantKey, LocalDate date) {
    Double best = null;
    for (RecordPoint point : history) {
      if (!point.variantKey().equals(variantKey) || point.date().isAfter(date)) {
        continue;
      }
      Double oneRm =
          point.reps() == 1 ? Double.valueOf(point.weightKg()) : LiftVariant.epley(point.weightKg(), point.reps());
      if (oneRm != null && (best == null || oneRm > best)) {
        best = oneRm;
      }
    }
    return Optional.ofNullable(best);
  }

  /**
   * The Monday of a day's week (ISO).
   *
   * @param day The day.
   * @return Its Monday.
   */
  public static LocalDate monday(LocalDate day) {
    return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }
}
