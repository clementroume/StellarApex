package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.PerSet;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.ScoreSet;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.TeamMode;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import apex.stellar.aldebaran.model.enums.UncountedReason;
import apex.stellar.aldebaran.model.enums.Unit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BalanceCalculatorTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
  private static final AtomicLong IDS = new AtomicLong(1);

  private static final Muscle QUADS = muscle("Quadriceps");
  private static final Muscle GLUTES = muscle("Glutes");
  private static final Muscle LATS = muscle("Latissimus");
  private static final Muscle ABS = muscle("Abdominals");
  private static final Muscle CHEST = muscle("Pectoralis");

  private static final Movement THRUSTER =
      movement("Thruster", Category.COMPLEXES, new PerSet(10, null, 60, null), QUADS, GLUTES, ABS);
  private static final Movement PULL_UP =
      movement("Pull-up", Category.PULLING, new PerSet(10, null, 60, null), LATS, null, ABS);
  private static final Movement PUSH_UP =
      movement("Push-up", Category.PUSHING, new PerSet(15, null, 60, null), CHEST, null, ABS);
  private static final Movement SQUAT =
      movement("Squat", Category.SQUAT, new PerSet(10, null, 60, null), QUADS, GLUTES, ABS);
  private static final Movement RUNNING =
      movement("Running", Category.CARDIO, new PerSet(null, 800, 240, null), QUADS, null, null);
  private static final Movement LUNGE =
      movement("Lunge", Category.LUNGES, new PerSet(10, 10, 60, null), QUADS, GLUTES, null);
  private static final Movement SHUTTLE =
      movement("Shuttle", Category.CARDIO, new PerSet(30, 450, 240, null), QUADS, null, null);
  private static final Movement BURPEE =
      movement("Burpee", Category.BURPEES, new PerSet(10, null, 60, null), CHEST, QUADS, null);

  private static Muscle muscle(String name) {
    return Muscle.builder().id(IDS.getAndIncrement()).medicalName(name).build();
  }

  private static Movement movement(
      String name, Category category, PerSet perSet, Muscle agonist, Muscle synergist, Muscle stabilizer) {
    Movement m =
        Movement.builder()
            .id(IDS.getAndIncrement())
            .name(name)
            .category(category)
            .perSet(perSet)
            .equipment(new HashSet<>(Set.of(Equipment.BARBELL, Equipment.DUMBBELL)))
            .build();
    m.getTargetedMuscles().add(MovementMuscle.builder().muscle(agonist).role(MuscleRole.AGONIST).build());
    if (synergist != null) {
      m.getTargetedMuscles().add(MovementMuscle.builder().muscle(synergist).role(MuscleRole.SYNERGIST).build());
    }
    if (stabilizer != null) {
      m.getTargetedMuscles().add(MovementMuscle.builder().muscle(stabilizer).role(MuscleRole.STABILIZER).build());
    }
    return m;
  }

  private static Wod wod(WodType type, ScoreType scoreType) {
    return Wod.builder().id(IDS.getAndIncrement()).title("W").wodType(type).scoreType(scoreType).build();
  }

  private static WodMovement line(Wod wod, Movement movement, String scheme) {
    WodMovement line =
        WodMovement.builder()
            .wod(wod)
            .movement(movement)
            .orderIndex(wod.getMovements().size() + 1)
            .repsScheme(scheme)
            .build();
    wod.getMovements().add(line);
    return line;
  }

  private static WeekTotals totals(Wod wod, Score score) {
    return totals(new Workout(wod, score, DAY, null, null, 0));
  }

  private static WeekTotals totals(Workout workout) {
    WeekTotals totals = new WeekTotals();
    BalanceCalculator.add(workout, BalanceCalculator.NearRecord.NEVER, totals);
    return totals;
  }

  private static double moving(WeekTotals totals, Muscle muscle) {
    WeekTotals.MuscleSets sets = totals.muscles().get(muscle.getId());
    return sets == null ? 0 : sets.moving();
  }

  private static double holding(WeekTotals totals, Muscle muscle) {
    WeekTotals.MuscleSets sets = totals.muscles().get(muscle.getId());
    return sets == null ? 0 : sets.holding();
  }

  private static Score timed(int seconds) {
    return Score.builder().date(DAY).timeSeconds(seconds).build();
  }

  @Test
  @DisplayName("Fran: 21-15-9 summed, agonists 1, synergists 0.5, stabilizers in holding, 5 min = short")
  void fran() {
    Wod fran = wod(WodType.FOR_TIME, ScoreType.TIME);
    line(fran, THRUSTER, "21-15-9");
    line(fran, PULL_UP, "21-15-9");

    WeekTotals totals = totals(fran, timed(300));

    assertThat(moving(totals, QUADS)).isCloseTo(4.5, within(1e-9));
    assertThat(moving(totals, GLUTES)).isCloseTo(2.25, within(1e-9));
    assertThat(moving(totals, LATS)).isCloseTo(4.5, within(1e-9));
    assertThat(holding(totals, ABS)).isCloseTo(9, within(1e-9));
    assertThat(totals.totalSets()).isCloseTo(9, within(1e-9));
    assertThat(totals.modalities()).containsEntry(Modality.WEIGHTLIFTING, 4.5).containsEntry(Modality.GYMNASTICS, 4.5);
    assertThat(totals.seconds()).containsEntry(TimeDomain.SHORT, 300);
    assertThat(totals.lifts()).isEmpty();
  }

  @Test
  @DisplayName("Cindy: the AMRAP score is unrolled in line order, whole rounds then the remainder")
  void cindy() {
    Wod cindy = wod(WodType.AMRAP, ScoreType.REPS);
    cindy.setTimeCapSeconds(1200);
    cindy.setRepsPerRound(30);
    line(cindy, PULL_UP, "5");
    line(cindy, PUSH_UP, "10");
    line(cindy, SQUAT, "15");
    // 20 rounds + 8 reps: 5 pull-ups and 3 push-ups
    Score score = Score.builder().date(DAY).reps(608).build();

    WeekTotals totals = totals(cindy, score);

    assertThat(moving(totals, LATS)).isCloseTo(105 / 10.0, within(1e-9));
    assertThat(moving(totals, CHEST)).isCloseTo(203 / 15.0, within(1e-9));
    assertThat(totals.seconds()).containsEntry(TimeDomain.MEDIUM, 1200);
  }

  @Test
  @DisplayName("An AMRAP checked without a score: one set per minute, shared by the lines' value in a round")
  void amrapWithoutScore() {
    Wod cindy = wod(WodType.AMRAP, ScoreType.REPS);
    cindy.setTimeCapSeconds(1200);
    line(cindy, PULL_UP, "5");
    line(cindy, PUSH_UP, "10");
    line(cindy, SQUAT, "15");

    WeekTotals totals = totals(new Workout(cindy, null, DAY, Kind.WOD, null, 0));

    // A round is worth 0.5 + 0.667 + 1.5 sets; 20 minutes = 20 sets in that proportion
    assertThat(totals.totalSets()).isCloseTo(20, within(1e-9));
    assertThat(moving(totals, LATS)).isCloseTo(20 * 0.5 / (0.5 + 10 / 15.0 + 1.5), within(1e-9));
  }

  @Test
  @DisplayName("Chelsea: an EMOM scored in rounds counts the rounds done, in intervals")
  void chelsea() {
    Wod chelsea = wod(WodType.EMOM, ScoreType.ROUNDS);
    chelsea.setStructure(WodStructure.EVERY_MINUTE);
    chelsea.setEmomInterval(60);
    chelsea.setEmomRounds(30);
    line(chelsea, PULL_UP, "5");
    line(chelsea, PUSH_UP, "10");
    line(chelsea, SQUAT, "15");

    WeekTotals totals = totals(chelsea, Score.builder().date(DAY).rounds(25).build());

    assertThat(moving(totals, LATS)).isCloseTo(12.5, within(1e-9));
    assertThat(totals.seconds()).containsEntry(TimeDomain.INTERVALS, 1500);
  }

  @Test
  @DisplayName("Murph: miles converted to metres, a run set is 800 m; buy-in and cash-out counted once")
  void murph() {
    Wod murph = wod(WodType.FOR_TIME, ScoreType.TIME);
    murph.setRounds(1);
    WodMovement in = line(murph, RUNNING, "");
    in.setDistance(1.0);
    in.setDistanceUnit(Unit.MILES);
    in.setSegment(Segment.BUY_IN);
    line(murph, PULL_UP, "100");
    WodMovement out = line(murph, RUNNING, "");
    out.setDistance(1.0);
    out.setDistanceUnit(Unit.MILES);
    out.setSegment(Segment.BUY_OUT);

    WeekTotals totals = totals(murph, timed(2700));

    assertThat(totals.modalities().get(Modality.MONOSTRUCTURAL)).isCloseTo(2 * 1609.344 / 800, within(1e-9));
    assertThat(totals.seconds()).containsEntry(TimeDomain.LONG, 2700);
  }

  @Test
  @DisplayName("Open 16.1: a distance line is worth the rest of the round in the unroll")
  void distanceLineInAmrap() {
    Wod open = wod(WodType.AMRAP, ScoreType.REPS);
    open.setTimeCapSeconds(1200);
    open.setRepsPerRound(26);
    WodMovement lunge = line(open, LUNGE, "");
    lunge.setDistance(25.0);
    lunge.setDistanceUnit(Unit.FEET);
    line(open, BURPEE, "8");
    WodMovement again = line(open, LUNGE, "");
    again.setDistance(25.0);
    again.setDistanceUnit(Unit.FEET);
    line(open, PULL_UP, "8");
    // Two rounds and the first lunges: 2 x 26 + 5
    WeekTotals totals = totals(open, Score.builder().date(DAY).reps(57).build());

    assertThat(moving(totals, LATS)).isCloseTo(1.6, within(1e-9));
    double lungeSets = 5 * 25 * 0.3048 / 10;
    assertThat(totals.modalities().get(Modality.GYMNASTICS)).isCloseTo(lungeSets + 1.6 + 1.6, within(1e-9));
  }

  @Test
  @DisplayName("A team line is split by the smaller of the team size and the members (a mirror does a share)")
  void team() {
    Wod partner = wod(WodType.FOR_TIME, ScoreType.TIME);
    partner.setTeamSize(2);
    partner.setTeamMode(TeamMode.YOU_GO_I_GO);
    line(partner, PULL_UP, "100");

    assertThat(moving(totals(new Workout(partner, timed(600), DAY, null, null, 3)), LATS))
        .isCloseTo(5, within(1e-9));
    assertThat(totals(new Workout(partner, timed(600), DAY, null, null, 2)).seconds())
        .containsEntry(TimeDomain.INTERVALS, 600);
  }

  @Test
  @DisplayName("Skill and Accessory blocks count no minutes")
  void skillBlockNoMinutes() {
    Wod skill = wod(WodType.FOR_TIME, ScoreType.TIME);
    line(skill, PULL_UP, "30");
    assertThat(totals(new Workout(skill, timed(400), DAY, Kind.SKILL, null, 0)).seconds()).isEmpty();
  }

  @Test
  @DisplayName("Strength: completed sets without warm-up, 1 to 5 reps worth half, one share per lift, near the record")
  void strengthSets() {
    Wod squat = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    WodMovement back = line(squat, SQUAT, "5 x 5");
    back.getEquipment().add("BARBELL");
    List<ScoreSet> sets = new ArrayList<>();
    sets.add(ScoreSet.builder().reps(5).weightKg(40.0).build());
    for (int i = 0; i < 5; i++) {
      sets.add(ScoreSet.builder().reps(5).weightKg(100.0).build());
    }
    sets.add(ScoreSet.builder().reps(5).weightKg(100.0).completed(false).build());
    Score score = Score.builder().date(DAY).maxWeightKg(100.0).sets(sets).build();

    WeekTotals totals = new WeekTotals();
    BalanceCalculator.add(
        new Workout(squat, score, DAY, null, null, 0), (m, key, day, load) -> load >= 100, totals);

    assertThat(moving(totals, QUADS)).isCloseTo(2.5, within(1e-9));
    assertThat(totals.lifts()).containsOnlyKeys("Squat");
    assertThat(totals.lifts().get("Squat").sets()).isCloseTo(2.5, within(1e-9));
    assertThat(totals.lifts().get("Squat").nearRecord()).isTrue();
    assertThat(totals.seconds()).isEmpty();
  }

  @Test
  @DisplayName("Strength without sets: the prescription, the prescribed percentage for near the record")
  void strengthPrescribed() {
    Wod squat = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    WodMovement back = line(squat, SQUAT, "8");
    back.setSets(4);
    back.setPercentOfRm(85.0);

    WeekTotals totals = totals(new Workout(squat, null, DAY, null, null, 0));

    assertThat(totals.lifts().get("Squat").sets()).isCloseTo(4, within(1e-9));
    assertThat(totals.lifts().get("Squat").nearRecord()).isTrue();
  }

  @Test
  @DisplayName("A complex: each set is a round, its reps summed, each muscle at its strongest role")
  void complex() {
    Wod complex = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    line(complex, THRUSTER, "1").setComplexGroup(1);
    line(complex, PULL_UP, "1").setComplexGroup(1);
    Score score =
        Score.builder()
            .date(DAY)
            .sets(new ArrayList<>(List.of(
                ScoreSet.builder().reps(1).weightKg(60.0).build(),
                ScoreSet.builder().reps(1).weightKg(60.0).build())))
            .build();

    WeekTotals totals = totals(complex, score);

    // Two rounds of 2 reps: half a set each
    assertThat(moving(totals, QUADS)).isCloseTo(1, within(1e-9));
    assertThat(moving(totals, LATS)).isCloseTo(1, within(1e-9));
    assertThat(holding(totals, ABS)).isCloseTo(1, within(1e-9));
    assertThat(totals.lifts()).containsOnlyKeys("Thruster + Pull-up");
  }

  @Test
  @DisplayName("Sets come before the format: an EMOM with sets is strength, without minutes")
  void emomWithSetsIsStrength() {
    Wod emom = wod(WodType.EMOM, ScoreType.NONE);
    emom.setEmomRounds(10);
    line(emom, SQUAT, "2");
    Score score =
        Score.builder().date(DAY).sets(new ArrayList<>(List.of(ScoreSet.builder().reps(2).weightKg(80.0).build()))).build();

    WeekTotals totals = totals(emom, score);

    assertThat(totals.lifts()).containsKey("Squat");
    assertThat(totals.seconds()).isEmpty();
  }

  @Test
  @DisplayName("Unreadable lines count 0; max lines share the score")
  void maxAndUnreadable() {
    Wod lynne = wod(WodType.AMRAP, ScoreType.REPS);
    line(lynne, PULL_UP, "max");
    line(lynne, PUSH_UP, "max");
    line(lynne, SQUAT, "beaucoup");

    WeekTotals totals = totals(lynne, Score.builder().date(DAY).reps(60).build());

    assertThat(moving(totals, LATS)).isCloseTo(3, within(1e-9));
    assertThat(moving(totals, CHEST)).isCloseTo(2, within(1e-9));
    assertThat(moving(totals, GLUTES)).isZero();
  }

  @Test
  @DisplayName("Contract for lot D': done or prescribed, the vector of several sessions is the sum of theirs")
  void vectorIsLinear() {
    Wod fran = wod(WodType.FOR_TIME, ScoreType.TIME);
    line(fran, THRUSTER, "21-15-9");
    line(fran, PULL_UP, "21-15-9");
    Wod squat = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    line(squat, SQUAT, "5 x 5");
    // A prescription: no score
    Workout done = new Workout(fran, timed(300), DAY, null, null, 0);
    Workout prescribed = new Workout(squat, null, DAY.plusDays(2), null, null, 0);

    WeekTotals together =
        BalanceCalculator.vectorOf(List.of(done, prescribed), BalanceCalculator.NearRecord.NEVER);
    WeekTotals summed =
        BalanceCalculator.sum(
            List.of(
                BalanceCalculator.vectorOf(List.of(done), BalanceCalculator.NearRecord.NEVER),
                BalanceCalculator.vectorOf(List.of(prescribed), BalanceCalculator.NearRecord.NEVER)));

    assertThat(summed.totalSets()).isCloseTo(together.totalSets(), within(1e-9));
    assertThat(moving(summed, QUADS)).isCloseTo(moving(together, QUADS), within(1e-9));
    assertThat(holding(summed, ABS)).isCloseTo(holding(together, ABS), within(1e-9));
    assertThat(summed.modalities()).isEqualTo(together.modalities());
    assertThat(summed.seconds()).isEqualTo(together.seconds());
    assertThat(summed.lifts().keySet()).isEqualTo(together.lifts().keySet());
    // The prescribed 5 x 5 counts: five half sets
    assertThat(together.lifts().get("Squat").sets()).isCloseTo(2.5, within(1e-9));
  }

  @Test
  @DisplayName("D121: the six written forms read, anything else does not; a blank text is not judged")
  void readableText() {
    Wod metcon = wod(WodType.FOR_TIME, ScoreType.TIME);
    for (String text : List.of("21", "21-15-9", "max", "1 min", "3-6-9-...", "5 x 5", "8 x 20 s", "  ")) {
      assertThat(BalanceCalculator.readableText(metcon, text)).as(text).isTrue();
    }
    assertThat(BalanceCalculator.readableText(metcon, null)).isTrue();
    for (String text : List.of("10 each side", "AMRAP", "21/15/9", "2 rounds")) {
      assertThat(BalanceCalculator.readableText(metcon, text)).as(text).isFalse();
    }
  }

  @Test
  @DisplayName("D121: the form judges a text as its WOD counts it (strength reps, N RM, Death by ladder)")
  void readableTextInContext() {
    Wod strength = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    assertThat(BalanceCalculator.readableText(strength, "5 x 5")).isTrue();
    assertThat(BalanceCalculator.readableText(strength, "1 min")).isFalse();
    assertThat(BalanceCalculator.readableText(strength, "5RM")).isFalse();
    strength.setRepMax(5);
    assertThat(BalanceCalculator.readableText(strength, "5RM")).isTrue();
    Wod deathBy = wod(WodType.EMOM, ScoreType.ROUNDS);
    assertThat(BalanceCalculator.readableText(deathBy, "+1 each minute")).isFalse();
    deathBy.setLadderStart(1);
    assertThat(BalanceCalculator.readableText(deathBy, "+1 each minute")).isTrue();
  }

  @Test
  @DisplayName("Admin control: an unreadable line, a unit without set value, strength judged as it counts")
  void uncounted() {
    Wod metcon = wod(WodType.FOR_TIME, ScoreType.TIME);
    WodMovement fine = line(metcon, THRUSTER, "21-15-9");
    WodMovement unreadable = line(metcon, PULL_UP, "10 each side");
    WodMovement noCalories = line(metcon, RUNNING, null);
    noCalories.setCalories(20);
    WodMovement nothing = line(metcon, BURPEE, null);
    assertThat(BalanceCalculator.uncounted(metcon, fine)).isNull();
    assertThat(BalanceCalculator.uncounted(metcon, unreadable)).isEqualTo(UncountedReason.UNREADABLE);
    assertThat(BalanceCalculator.uncounted(metcon, noCalories)).isEqualTo(UncountedReason.NO_SET_VALUE);
    assertThat(BalanceCalculator.uncounted(metcon, nothing)).isEqualTo(UncountedReason.UNREADABLE);

    Wod deathBy = wod(WodType.EMOM, ScoreType.ROUNDS);
    deathBy.setLadderStart(1);
    assertThat(BalanceCalculator.uncounted(deathBy, line(deathBy, BURPEE, null))).isNull();

    Wod strength = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    assertThat(BalanceCalculator.uncounted(strength, line(strength, RUNNING, "5 x 5"))).isNull();
    assertThat(BalanceCalculator.uncounted(strength, line(strength, SQUAT, "1 min"))).isEqualTo(UncountedReason.UNREADABLE);
    Wod fiveRm = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    fiveRm.setRepMax(5);
    assertThat(BalanceCalculator.uncounted(fiveRm, line(fiveRm, SQUAT, "5RM"))).isNull();
  }

  @Test
  @DisplayName("A line without position goes last instead of failing the week")
  void lineWithoutPosition() {
    Wod strength = wod(WodType.FOR_LOAD, ScoreType.WEIGHT);
    line(strength, SQUAT, "5 x 5").setOrderIndex(null);
    line(strength, THRUSTER, "3 x 3");
    WeekTotals totals = totals(new Workout(strength, null, DAY, null, null, 0));
    assertThat(totals.lifts()).containsKeys("Squat", "Thruster");
  }

  @Test
  @DisplayName("Shuttle runs: 30 reps of the usual 15 m out and back per set, a longer course in proportion")
  void shuttleLength() {
    Wod usual = wod(WodType.FOR_TIME, ScoreType.TIME);
    WodMovement standard = line(usual, SHUTTLE, "30");
    standard.setLength(7.5);
    standard.setLengthUnit(Unit.METERS);
    assertThat(totals(usual, timed(240)).totalSets()).isCloseTo(1.0, within(1e-9));

    Wod longer = wod(WodType.FOR_TIME, ScoreType.TIME);
    WodMovement fifty = line(longer, SHUTTLE, "30");
    fifty.setLength(50.0);
    fifty.setLengthUnit(Unit.METERS);
    // 30 x 100 m = 3000 m of shuttles: 3000 / 450
    assertThat(totals(longer, timed(900)).totalSets()).isCloseTo(3000.0 / 450, within(1e-9));

    Wod noLength = wod(WodType.FOR_TIME, ScoreType.TIME);
    line(noLength, SHUTTLE, "30");
    assertThat(totals(noLength, timed(240)).totalSets()).isCloseTo(1.0, within(1e-9));
  }
}
