package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.OrderApplyRequest;
import apex.stellar.aldebaran.dto.OrderApplyRequest.DayOrder;
import apex.stellar.aldebaran.dto.OrderApplyRequest.WeekOrder;
import apex.stellar.aldebaran.dto.OrderProposalRequest;
import apex.stellar.aldebaran.dto.OrderProposalResponse;
import apex.stellar.aldebaran.dto.OrderProposalResponse.DayProposal;
import apex.stellar.aldebaran.dto.OrderProposalResponse.PartView;
import apex.stellar.aldebaran.dto.OrderProposalResponse.WeekProposal;
import apex.stellar.aldebaran.exception.InvalidSessionException;
import apex.stellar.aldebaran.exception.WodLockedException;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.OrderMode;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.SessionBlockRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.security.SessionSecurity;
import apex.stellar.aldebaran.service.ProgrammingAlerts.MuscleInfo;
import apex.stellar.aldebaran.service.SessionOrder.Day;
import apex.stellar.aldebaran.service.SessionOrder.Part;
import apex.stellar.aldebaran.service.SessionOrder.Result;
import apex.stellar.aldebaran.service.SessionOrder.Week;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The session-order proposal of a gym's programming and its application (lot D', D168-D171). The
 * proposal reads the CrossFit sessions of the cycle as the balance does (same switches), splits
 * each session into its Skill part and its WOD part (WOD, Accessory, Finisher) and lets {@link
 * SessionOrder} search the order. The application checks rights, locks and that each week only
 * swaps its own parts, then moves blocks between the week's sessions, or whole sessions between
 * days, in one transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionOrderService {

  private static final Set<Kind> WOD_PART = Set.of(Kind.WOD, Kind.ACCESSORY, Kind.FINISHER);

  private final SessionRepository sessionRepository;
  private final SessionBlockRepository sessionBlockRepository;
  private final SessionLogRepository sessionLogRepository;
  private final ScoreRepository scoreRepository;
  private final ProgrammingBalanceService programmingBalanceService;
  private final SessionService sessionService;
  private final SessionSecurity sessionSecurity;

  /**
   * Proposes an order of the cycle's sessions.
   *
   * @param gymId The gym (rights checked by the controller: WOD_WRITE or admin).
   * @param request The cycle, the switches, the mode, the strength-day option and the pins.
   * @return The proposal; nothing is written.
   */
  @Transactional(readOnly = true)
  public OrderProposalResponse propose(Long gymId, OrderProposalRequest request) {
    int weeks = request.weeks();
    if (weeks < ProgrammingBalanceService.MIN_WEEKS || weeks > ProgrammingBalanceService.MAX_WEEKS) {
      throw new InvalidSessionException(
          "error.programming.weeks", ProgrammingBalanceService.MIN_WEEKS, ProgrammingBalanceService.MAX_WEEKS);
    }
    LocalDate start = monday(request.start());
    LocalDate end = start.plusWeeks(weeks).minusDays(1);
    Map<Long, MuscleInfo> muscles = programmingBalanceService.muscles();
    Map<LocalDate, List<Session>> byDate = new TreeMap<>();
    for (Session session : sessionRepository.findProgramming(gymId, start.minusDays(1), end)) {
      if (ProgrammingBalanceService.counts(session) && session.getBlocks().stream().anyMatch(this::moves)) {
        byDate.computeIfAbsent(session.getDate(), d -> new ArrayList<>()).add(session);
      }
    }
    Reader reader = new Reader(request, muscles);
    Part dayBefore = null;
    List<Session> before = byDate.getOrDefault(start.minusDays(1), List.of());
    if (before.size() == 1) {
      dayBefore = reader.skill(before.getFirst());
    }
    List<Week> cycle = new ArrayList<>();
    for (int w = 0; w < weeks; w++) {
      LocalDate monday = start.plusWeeks(w);
      List<Day> days = new ArrayList<>();
      for (int d = 0; d < 7; d++) {
        LocalDate date = monday.plusDays(d);
        List<Session> sessions = byDate.getOrDefault(date, List.of());
        if (!sessions.isEmpty()) {
          days.add(reader.day(date, sessions));
        }
      }
      cycle.add(new Week(monday, days));
    }
    Result result = SessionOrder.propose(cycle, muscles, request.mode(), request.heavy(), dayBefore);
    return new OrderProposalResponse(
        start,
        end,
        request.mode(),
        request.heavy(),
        score(result.before()),
        score(result.after()),
        weekViews(cycle, result.weeks(), reader));
  }

  /**
   * Applies the weeks of a proposal the coach keeps.
   *
   * @param gymId The gym (rights checked by the controller: WOD_WRITE or admin).
   * @param request The weeks kept.
   * @throws WodLockedException if a session became locked since the proposal (409).
   * @throws InvalidSessionException if a week does not only swap its own parts (400).
   */
  @Transactional
  public void apply(Long gymId, OrderApplyRequest request) {
    for (WeekOrder week : request.weeks()) {
      applyWeek(gymId, request.mode(), week);
    }
  }

  private void applyWeek(Long gymId, OrderMode mode, WeekOrder week) {
    LocalDate monday = monday(week.monday());
    Map<Long, Session> sessions = new HashMap<>();
    for (Session s : sessionRepository.findProgramming(gymId, monday, monday.plusDays(6))) {
      sessions.put(s.getId(), s);
    }
    Map<Long, SessionBlock> blocks = new HashMap<>();
    Set<Long> expected = new HashSet<>();
    Set<Long> seen = new HashSet<>();
    for (DayOrder day : week.days()) {
      Session s = sessions.get(day.sessionId());
      if (s == null || !ProgrammingBalanceService.counts(s) || !seen.add(s.getId())) {
        throw new InvalidSessionException("error.order.invalid", monday);
      }
      if (locked(s)) {
        throw new WodLockedException("error.order.locked", s.getId());
      }
      for (SessionBlock b : s.getBlocks()) {
        if (moves(b)) {
          blocks.put(b.getId(), b);
          expected.add(b.getId());
        }
      }
    }
    // The week only swaps its own movable blocks, each once, each in its own kind of part
    List<Long> given = new ArrayList<>();
    for (DayOrder day : week.days()) {
      for (Long id : day.skillBlockIds()) {
        SessionBlock b = blocks.get(id);
        if (b == null || b.getKind() != Kind.SKILL) {
          throw new InvalidSessionException("error.order.invalid", monday);
        }
        given.add(id);
      }
      for (Long id : day.wodBlockIds()) {
        SessionBlock b = blocks.get(id);
        if (b == null || !WOD_PART.contains(b.getKind())) {
          throw new InvalidSessionException("error.order.invalid", monday);
        }
        given.add(id);
      }
    }
    if (given.size() != expected.size() || !expected.equals(new HashSet<>(given))) {
      throw new InvalidSessionException("error.order.invalid", monday);
    }
    // WOD only: every Skill stays in its own session
    if (mode == OrderMode.WOD_ONLY) {
      for (DayOrder day : week.days()) {
        for (Long id : day.skillBlockIds()) {
          if (!blocks.get(id).getSession().getId().equals(day.sessionId())) {
            throw new InvalidSessionException("error.order.invalid", monday);
          }
        }
      }
    }
    if (mode == OrderMode.WHOLE_SESSIONS) {
      moveSessions(week, sessions, blocks, monday);
    } else {
      moveBlocks(week, sessions, blocks);
    }
    log.info("Session order applied to the week of {} (gym {}, {})", monday, gymId, mode);
  }

  /** Whole sessions: each day receives the session whose blocks it lists. */
  private void moveSessions(
      WeekOrder week, Map<Long, Session> sessions, Map<Long, SessionBlock> blocks, LocalDate monday) {
    Map<Session, LocalDate> dates = new HashMap<>();
    for (DayOrder day : week.days()) {
      Set<Session> owners = new HashSet<>();
      day.skillBlockIds().forEach(id -> owners.add(blocks.get(id).getSession()));
      day.wodBlockIds().forEach(id -> owners.add(blocks.get(id).getSession()));
      if (owners.size() != 1) {
        throw new InvalidSessionException("error.order.invalid", monday);
      }
      dates.put(owners.iterator().next(), sessions.get(day.sessionId()).getDate());
    }
    dates.forEach((session, date) -> {
      if (!date.equals(session.getDate())) {
        sessionService.moveDate(session, date);
      }
    });
  }

  /** Parts: each day's session receives the blocks it lists, around its own text blocks. */
  private void moveBlocks(WeekOrder week, Map<Long, Session> sessions, Map<Long, SessionBlock> blocks) {
    for (DayOrder day : week.days()) {
      Session target = sessions.get(day.sessionId());
      List<SessionBlock> first = new ArrayList<>();
      List<SessionBlock> last = new ArrayList<>();
      for (SessionBlock b : target.getBlocks()) {
        if (!moves(b)) {
          (b.getKind() == Kind.WARM_UP ? first : last).add(b);
        }
      }
      List<SessionBlock> ordered = new ArrayList<>(first);
      day.skillBlockIds().stream().map(blocks::get).sorted(byOrder()).forEach(ordered::add);
      day.wodBlockIds().stream().map(blocks::get).sorted(byKindThenOrder()).forEach(ordered::add);
      ordered.addAll(last);
      for (int i = 0; i < ordered.size(); i++) {
        sessionBlockRepository.moveTo(ordered.get(i).getId(), target, i);
      }
    }
  }

  private static Comparator<SessionBlock> byOrder() {
    return Comparator.comparing(SessionBlock::getOrderIndex);
  }

  private static Comparator<SessionBlock> byKindThenOrder() {
    return Comparator.comparing((SessionBlock b) -> b.getKind().ordinal()).thenComparing(SessionBlock::getOrderIndex);
  }

  /** A block that moves with its part: a Skill or WOD-part block holding a WOD. */
  private boolean moves(SessionBlock block) {
    return block.getWod() != null && (block.getKind() == Kind.SKILL || WOD_PART.contains(block.getKind()));
  }

  /** Never moved: past, logged (a journal, so scores too), or holding a competition WOD (D168). */
  private boolean locked(Session session) {
    return session.getDate().isBefore(sessionSecurity.today(session))
        || sessionLogRepository.existsBySessionId(session.getId())
        || !scoreRepository.findScoredBlockIdsInSession(session.getId()).isEmpty()
        || session.getBlocks().stream().anyMatch(b -> b.getWod() != null && b.getWod().isCompetition());
  }

  private static LocalDate monday(LocalDate day) {
    return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private static OrderProposalResponse.Score score(SessionOrder.Score s) {
    return new OrderProposalResponse.Score(
        s.profilesInAlert(), Math.round(s.excess() * 100) / 100.0, s.sameDomain(), s.moves());
  }

  private List<WeekProposal> weekViews(List<Week> before, List<Week> after, Reader reader) {
    List<WeekProposal> out = new ArrayList<>();
    for (int w = 0; w < before.size(); w++) {
      List<Day> was = before.get(w).days();
      List<Day> now = after.get(w).days();
      boolean changed = false;
      List<DayProposal> days = new ArrayList<>();
      for (int d = 0; d < was.size(); d++) {
        Day a = was.get(d);
        Day b = now.get(d);
        changed |= a.skill() != b.skill() || a.wod() != b.wod();
        LocalDate date = before.get(w).monday().plusDays(a.day().ordinal());
        days.add(
            new DayProposal(
                a.day(),
                date,
                reader.sessionOf(date),
                a.locked(),
                reader.view(a.skill()),
                reader.view(a.wod()),
                reader.view(b.skill()),
                reader.view(b.wod())));
      }
      out.add(new WeekProposal(before.get(w).monday(), changed, days));
    }
    return out;
  }

  /** Reads sessions into parts, and parts back into views. */
  private final class Reader {
    private final OrderProposalRequest request;
    private final Map<Long, MuscleInfo> muscles;
    private final Map<String, PartInfo> infos = new HashMap<>();
    private final Map<LocalDate, Long> sessionOfDate = new HashMap<>();

    Reader(OrderProposalRequest request, Map<Long, MuscleInfo> muscles) {
      this.request = request;
      this.muscles = muscles;
    }

    Day day(LocalDate date, List<Session> sessions) {
      sessionOfDate.put(date, sessions.getFirst().getId());
      if (sessions.size() > 1) {
        // Two sessions that day: read together, never moved
        List<SessionBlock> all = sessions.stream().flatMap(s -> s.getBlocks().stream()).toList();
        return new Day(date.getDayOfWeek(), null, part("L" + date, all, date, false), true);
      }
      Session s = sessions.getFirst();
      return new Day(date.getDayOfWeek(), skill(s), wod(s), locked(s));
    }

    @Nullable Part skill(Session s) {
      List<SessionBlock> blocks = s.getBlocks().stream().filter(b -> b.getWod() != null && b.getKind() == Kind.SKILL).toList();
      return blocks.isEmpty() ? null : part("S" + s.getId(), blocks, s.getDate(), true);
    }

    @Nullable Part wod(Session s) {
      List<SessionBlock> blocks =
          s.getBlocks().stream().filter(b -> b.getWod() != null && WOD_PART.contains(b.getKind())).toList();
      return blocks.isEmpty() ? null : part("W" + s.getId(), blocks, s.getDate(), false);
    }

    private Part part(String key, List<SessionBlock> blocks, LocalDate date, boolean skill) {
      WeekTotals vector = new WeekTotals();
      Set<String> groups = new HashSet<>();
      boolean strength = false;
      for (SessionBlock b : blocks) {
        Wod wod = b.getWod();
        if (wod == null) {
          continue;
        }
        if (!b.isOptional() || request.optional()) {
          BalanceCalculator.add(
              Workout.prescribed(wod, date, b.getKind(), request.scaled()), BalanceCalculator.NearRecord.NEVER, vector);
        }
        if (skill) {
          strength |= wod.getWodType() == WodType.FOR_LOAD
              || wod.getScoreType() == ScoreType.WEIGHT
              || wod.getScoreType() == ScoreType.LOAD;
          groups.addAll(agonistGroups(wod));
        }
      }
      boolean pinned = blocks.stream().anyMatch(b -> request.pinnedBlockIds().contains(b.getId()));
      String title = blocks.stream().map(b -> b.getWod().getTitle()).distinct().collect(Collectors.joining(" + "));
      infos.put(
          key, new PartInfo(blocks.stream().map(SessionBlock::getId).toList(), title, date.getDayOfWeek()));
      return new Part(key, vector, pinned, strength && skill, groups);
    }

    /** Main groups where a line of the WOD has an agonist role (D169, soft option). */
    private Set<String> agonistGroups(Wod wod) {
      Set<String> groups = new HashSet<>();
      for (WodMovement line : wod.getMovements()) {
        CatalogRules.effectiveRoles(line.getMovement(), line.getTechniques(), line.getEquipment())
            .forEach(
                (muscle, role) -> {
                  if (role != MuscleRole.AGONIST) {
                    return;
                  }
                  MuscleInfo info = muscles.get(muscle.getId());
                  if (info == null) {
                    return;
                  }
                  for (String group : ProgrammingAlerts.MAIN_GROUPS) {
                    if (group.equals(info.code()) || group.equals(info.groupCode())) {
                      groups.add(group);
                    }
                  }
                });
      }
      return groups;
    }

    @Nullable PartView view(@Nullable Part part) {
      if (part == null) {
        return null;
      }
      PartInfo info = infos.get(part.key());
      return new PartView(info.blockIds(), info.title(), part.pinned(), part.strength(), info.day());
    }

    Long sessionOf(LocalDate date) {
      return sessionOfDate.get(date);
    }
  }

  /** What a part shows: its blocks, title and day of origin. */
  private record PartInfo(List<Long> blockIds, String title, DayOfWeek day) {}
}
