# Aldebaran Training API

This is the training and performance backend service for the StellarApex platform, responsible for
the exercise catalog (Muscles/Movements), workout definitions (WODs) and athlete performance tracking
(Scores/PRs, leaderboards). It is a secure, stateless Spring Boot application designed to run as a
containerized microservice behind Traefik.

## Tech Stack

* **Framework**: Spring Boot 4.1
* **Language**: Java 25
* **Security**: Spring Security 7 (stateless), identity received from Antares via Traefik Forward Auth
* **Database**: PostgreSQL (schema `aldebaran`, managed by Flyway migrations)
* **Cache**: Redis (WODs and reference data)
* **Tooling**: MapStruct (DTO mapping), Lombok (boilerplate reduction)
* **API Docs**: Springdoc 3.1 (OpenAPI, aggregated by Bellatrix)
* **Tests**: JUnit 5, Mockito, Testcontainers (PostgreSQL, Redis)

## Prerequisites

* JDK 25
* Docker & Docker Compose
* A configured `.env` file in the project root (see the root `README.md`).

## How to Build

This project uses the Maven Wrapper (`mvnw`), which will download the correct Maven version
automatically.

### Build the JAR

To build the executable JAR and run all tests:

```bash
./mvnw verify
```

This command runs all unit tests (`*Test.java`) and integration tests (`*IT.java`). Integration tests
start PostgreSQL and Redis with Testcontainers: Docker must be running. All ITs share one container pair and one Spring context; tables are
truncated before each test.

To build the JAR without running tests:

```bash
./mvnw clean package -DskipTests
```

### Docker

This project uses a multi-stage Dockerfile to create a minimal, non-root, distroless JRE image.
The image is built as part of the root `docker-compose.yml`.

```bash
# From the project root
docker compose build aldebaran-training
```

## How to Run

This service is designed to be run as part of the full StellarApex stack using Docker Compose. This
ensures all dependencies (Database, Redis, Traefik, Antares) are correctly networked and configured.

```bash
# From the project root
docker compose up --build -d
docker compose logs -f aldebaran-training
```

The API is available at https://stellar.apex/aldebaran (port 8081 inside the Docker network).

## Identity and Authorization

Aldebaran has no login of its own. Traefik calls Antares (`/antares/auth/verify/api`) before every
request and forwards these headers, checked with `X-Internal-Secret`:

* `X-Auth-User-Id`, `X-Auth-User-Locale`
* `X-Auth-User-Role`: platform role (`ADMIN` or `USER`)
* `X-Auth-Memberships`: usable gym memberships with effective permissions, e.g.
  `12:COACH:WOD_WRITE+SCORE_VERIFY,15:ATHLETE:`
* `X-Auth-User-Division`: `MEN`, `WOMEN` or empty (athlete profile in Antares), copied on the
  scores the user logs
* `X-Auth-User-Birth-Year`: year or empty, never stored: only the age group of the score's year is
  copied on the scores the user logs (`scores.age_group`)
* `X-Auth-Actor-Id`: set when a platform admin acts as this user through an audited support
  session. The other headers then describe the target user, whose rights apply unchanged; every
  non-GET request is logged at INFO (actor, user, method, path, status) by
  `security/SupportSessionAuditFilter`.

Rights are evaluated on the gym of each resource, so a member of several gyms can read and act in all
of them:

* **WOD read**: public, from one of the user's gyms, or authored by the user.
* **WOD create / update / delete**: `WOD_WRITE` on the WOD's gym; personal WODs (`gymId = null`) by
  their author only. A WOD with logged scores is locked (409). Ownership (`gymId`, `authorId`) cannot
  change on update.
* **Scores**: athletes log scores for themselves on the WODs they can read and manage their own.
  Each score records the gym where it was performed (`gymId`): the WOD's gym for a gym WOD, else one
  of the athlete's gyms chosen when logging (or none). `SCORE_VERIFY` on that gym allows correcting
  or deleting other athletes' scores (errors, cheating), including on public WODs such as the Open.
  Logging a score for someone else is admin-only, except on competition WODs (below). Each score
  carries `capabilities {canModify, canVerify}`; other athletes' comments are hidden
  from viewers who cannot moderate the score.
* **Score entry (lot S)**: a score is `RX` (nothing changed: a changed line is refused, 400) or
  `SCALED`, detailed per line in `lines` (variant done, reps, real load, distance, calories, height,
  duration; only the lines that changed). A Scaled score that follows the WOD's official Scaled
  version (`movements[].scaled`, set by the WOD's author, locked with the WOD) is `scaledOfficial`
  and ranked on the Scaled leaderboard; any other Scaled is never ranked, an `rxPlus` one (every
  line at least RX, one harder, the catalog's "counts for" rule for a variant) included. PR order:
  RX, official Scaled, free Scaled. Strength sets name their line (`sets[].wodMovementId`, a complex
  its first line); a `LOAD` score is the sum of each line's best complete set (CrossFit Total).
  Loads count with the box equivalences between kg and lb (`movements[].equivalents`: 135 lb =
  60 kg, exact conversion in competitions), so 60 kg on a 135 lb line is RX.
* **Team WODs** (former V16): `teamSize` (2–6) and `teamMode` (`YOU_GO_I_GO`, `SPLIT`, `SYNCHRO`) on the WOD,
  `teamSplit` (`TOTAL` default, `EACH`, `TOGETHER`) on each line; never on a program (400). A team
  score names teammates of the box (`teammateIds`, at most `teamSize`: an odd athlete mirrors a team;
  members checked with Antares, a program refused): one `score_teams` row, one score per member
  (PRs of each), division MEN / WOMEN / MIXED. Corrections reach every member; the logger, a judge
  or an admin deletes the team score, a teammate deleting their row leaves the team. Leaderboards
  keep one row per team (`teamId`, `teamMemberIds`).
* **Competition WODs** (`competition`, physical gym WODs only, never public: 400 otherwise; Antares
  tells the gym type): staff holding
  `SCORE_VERIFY` in the WOD's gym log scores for the gym's members (`userId`; Antares confirms the
  membership through its internal API with `X-Internal-Secret`, `client/AntaresClient`; 503 if
  Antares does not answer, never authorized by default). Such a score is submitted and verified at
  once. An athlete chooses to submit their own score (`verificationRequested`); only submitted
  scores are verified (others are plain scores). Staff verify them or withdraw the verification
  (never on their own score). A verified
  score is locked for its athlete (staff included); the staff can still correct it. Every score
  records who logged it (`loggedBy`) and who verified it (`verifiedBy`, `verifiedAt`). Scores are
  verified on competition WODs only.
* **Sessions** (lot B, below): frame read by the members of the session's gym, content per the
  session's visibility policy (staff of any role always), written with `WOD_WRITE`; personal
  sessions by their author only.
* **Muscles / movements**: read by everyone, written (and exported to CSV) by platform ADMIN only.
* A platform ADMIN can do everything.

Rules live in `security/WodSecurity`, `security/ScoreSecurity` and `security/SessionSecurity`, used
from `@PreAuthorize`.

## Sessions (lot B)

A session (`sessions`, former V11) is an ordered container of blocks (`session_blocks`) proposed on a day
(`type = WOD`, run in several slots) or a week (`type = SPECIALTY`, `date` = its Monday, `endDate` its
Sunday); the same blocks at two dates are two sessions. Disciplines: `CROSSFIT`, `WEIGHTLIFTING`,
`STRENGTH`, `GYMNASTICS`, `ENDURANCE`, `HYROX`, `MOBILITY`, `BODYBUILDING`, `STRONGMAN`, `OTHER`.

* Blocks: `WARM_UP` / `COOL_DOWN` are FR/EN text; `SKILL` (skill or strength), `WOD`, `ACCESSORY`,
  `FINISHER` carry a WOD, either created for the session (`wods.standalone = false`: hidden from WOD
  lists, read and edited through its session only) or borrowed from the standalone WODs (Fran: scores
  join Fran's leaderboard). A WOD session needs a `SKILL` and a `WOD` block; a specialty or a personal
  session at least one block with a WOD. Each block has an optional `label`, a thematic summary
  (`themeEn/Fr`, shown in advance), `optional` (offered on top), `plannedMinutes` and a `stimulus`
  (derived when omitted: `SessionService.deriveStimulus`).
* Competition block (`competition`, physical box only): the block's WOD becomes a competition WOD of
  the box (a borrowed public WOD such as the Open is copied). A competition WOD cannot be borrowed as
  is.
* Visibility of the content (frame = date, type, discipline, block kinds and themes, always shown to
  members): `ADVANCE` (instant chosen before the session), `DAY_OF` (instant on a day of the session),
  `AFTER` (day after the last day, 00:00), `AFTER_PARTICIPANTS` (`AFTER`, and each participant from
  their journal), `PARTICIPANTS_ONLY`. A program only uses `ADVANCE` / `DAY_OF`. `contentVisibleFrom`
  is computed with the gym's time zone. Staff (Owner, Programmer, Coach) always read the content;
  hidden content is null in responses and session WODs and their leaderboards answer 403.
* Gym settings (`gym_session_settings`): `defaultVisibility` (built-in: `AFTER_PARTICIPANTS` for a
  box, `ADVANCE` for a program, read from Antares) and `timeZone` (default `Europe/Paris`).
* Journal (former V12, `session_logs` + `session_log_blocks`): "Séance faite" creates the athlete's journal
  (one per session, on one of its days, never in the future) with every block checked; blocks can be
  unchecked; `source` `MANUAL` (`PLANNING` reserved for the planning service). A score on a session
  WOD joins the athlete's journal (created when missing, e.g. by a judge); a borrowed WOD's score
  joins it with `sessionLogId`. A journal makes its athlete a participant; it cannot be deleted while scores are linked.
  **Lock per block** (`SessionService`, `BlockLocks`): a block holding scores (`scores.session_block_id`,
  former V17, set with the journal: `ScoreRequest.sessionBlockId`, else the first block of the session
  holding the WOD; two blocks may borrow the same WOD) is *frozen* (kind, WOD written or borrowed, removal: 409); a block *checked*
  in a journal (`session_log_blocks.done`) without score cannot be removed (409) but stays fully
  editable, its prescription may change (lot D recomputes the muscle balance); free blocks, title,
  texts, themes, ordering, new blocks and visibility stay editable. With a journal the session's date,
  type, discipline and deletion are locked (409).
* Strength sets (`score_sets`): `sets [{reps, weight, completed}]` on `WEIGHT`, `LOAD` and `NONE`
  scores; the heaviest complete set becomes `maxWeight` (PR and leaderboard unchanged), the sum of
  reps × load of complete sets `totalLoad`. `wod_movements.sets` is the prescribed number of sets.

## Catalog Model

Lot M: a movement has equipment and typed techniques. Each technique type states what it counts for
(`service/VariantDominance`). A movement can be a chain (`movement_components`, with a "counts for records"
flag) or an "either" movement (`movement_accepted`). It holds role adjustments per technique or equipment
(`movement_role_adjustments`, lowest role wins), usual names with no identity of their own
(`movement_usual_names`), required values (`movement_required_rules`), the number of dumbbells of its base
version (`movement_dumbbell_bases`), and a joint layer that controls the roles (`movement_joint_actions`,
`muscle_joint_actions`). A WOD line is written in one canonical form and checked by `service/CatalogRules`;
`WodMovementResponse.display` says how it reads.

* **Value of one set** (lot D, D126): `reps_per_set`, `meters_per_set`, `seconds_per_set`, `calories_per_set` on
  a movement and, for a variant whose set differs (Single-under 100, Handstand Walk 15 m, Air Squat 20), on a
  usual name (`PerSet`, `MovementResponse.perSet`, `UsualNameDto.perSet`). The weekly balance divides a line by the
  value of its unit, read on its most precise usual name then on the movement (`CatalogRules.perSet`); nothing =
  the line counts 0. All in the catalog, no default in the code.
* **Modality of a line** (D127, `CatalogRules.modality`): the usual name's (`UsualNameDto.modality`, Air Squat =
  gymnastics), else the family's (`Category.Modality`: weightlifting, gymnastics, monostructural as in the CrossFit
  Level 1; strongman is a session discipline, not a modality); a gymnastics or cardio line carrying a load (weight,
  % of RM, load position, `WEIGHTED`) is weightlifting. Equipment alone never decides (deficit plates, assistance
  band, rings are apparatus). `wods.modalities` is computed with the same rule (Cindy: gymnastics only).
* **Roles** weigh a muscle alone: `impact_factor` was dropped (D130, it counted the role twice).
* **Movement patterns** (lot D', D175-D179, Flyway V11): `patterns` on a movement and on each usual name, values of
  `MovementPattern` = the axes of the coach's programming radar (Squat, Hinge, Push V/H, Pull V/H, Lunge, Locomotion:
  Carry / Gait / Crawl / Cyclic / Jump, Core: Flexion / Extension / Rotation / Lateral Flexion; a hold counts in the
  direction it works: Plank = Core Flexion, overhead = Push V, a hang = Pull V). A line reads its most precise usual
  name's, else the movement's, else (a chain without its own) the union of its components' (`CatalogRules.patterns`);
  a line counts once per axis, its details are shown on demand. Edited in the Sirius catalog forms, exported in the
  CSV backup (`patterns` column). Values reviewed in `docs-private/catalogue/revue-schemas-mouvement.md`.

* **Muscle code**: `code` is the stable key of a muscle in the 3D model of Sirius (`sirius-app/tools/muscle-model`),
  returned in `MuscleResponse` and `MuscleRefResponse`. Derived once from the medical name (`Quadriceps Femoris` gives
  `quadriceps-femoris`, numbered if taken), never changed: `medical_name` stays editable.
* **Muscles**: a `region` (`THORAX`, `BACK`, `ABDOMEN_PELVIS`, `SHOULDER`, `ARM_FOREARM`, `HIP`, `THIGH_LEG`) and an
  optional functional group (`parent`, e.g. Rectus Femoris in Quadriceps Femoris). One level only,
  same region as the group (409 otherwise). A movement targets a group, or one of its muscles when
  that muscle has its own action (e.g. Rectus Femoris as synergist of hip flexion).
* **Movements**: variants are the `equipment` and `techniques` of the movement (Ring Dip = Dip +
  `RINGS`), never a new movement or a note. "Any style" movements match WOD prescriptions (Clean,
  Snatch, Shoulder-to-Overhead, Ground-to-Overhead). Coaching cues: one cue per line.
* **WOD movements**: their equipment and techniques must be allowed by the movement
  (`WodMovement.checkVariantsAllowed`): 400 on create/update, also enforced when reading the CSVs.
  Conversely, a movement update cannot remove an equipment or technique that any WOD (public, gym
  or personal) still prescribes for it: 409 naming the value and the number of WODs.
* **WOD format and category** (formats v3, former V15): `wodType` is the format category (`FOR_TIME`, `AMRAP`,
  `EMOM`, `TABATA`, `FOR_LOAD`, `FOR_QUALITY`) and `structure` its sub-category (`Wod.WodStructure`,
  `allowedIn(type)`, 400 `error.wod.structure.mismatch` otherwise; listed per format in
  `GET /wods/reference-data` `structures`); a weightlifting complex groups its lines with
  `wod_movements.complex_group`. The former V15 migration moved the former types (CHIPPER, LADDER, INTERVALS, DEATH_BY,
  STRENGTH, SKILL, ACCESSORY, CUSTOM) to a category and a sub-category; the optional `category` is the family
  (`GIRLS`, `HERO`, `BENCHMARK` = official CrossFit benchmarks such as Fight Gone Bad, `OPEN`,
  `QUARTERFINALS`, `SEMIFINALS`, `GAMES`): categories of public WODs (former V13 dropped the former box
  benchmark category; box programming goes through sessions).
  Fran is a `FOR_TIME` / `SCHEME` WOD (`repScheme` 21-15-9) of the `GIRLS` category.
* **WOD structure** (former V14, written by the Sirius quick editor): `rounds` (a "rounds for time": Helen
  = `FOR_TIME` + 3), `restSeconds` (between rounds, or the rest of an interval), `workSeconds`
  (intervals, Tabata 20 s), `ladderStart` / `ladderStep` (ascending ladder 3-6-9..., Death by 1 + 1),
  weight vest `vestWeight` / `vestWeightFemale` / `vestWeightUnit` (Murph), and per WOD movement
  `segment` = `BUY_IN`, `MAIN` (default) or `BUY_OUT`. A descending ladder and strength sets stay in
  `repScheme`.
* **WOD texts and loads**: description and notes, and each WOD movement's notes and scaling options,
  exist in English and French (`*En` / `*Fr`, either may be empty; clients fall back on the other
  language). A WOD movement holds men's / women's pairs: load (`weight` / `weightFemale`, one
  `weightUnit`), calories (`calories` / `caloriesFemale`) and box or target height (`height` /
  `heightFemale`, one `heightUnit`: `CENTIMETERS`, `INCHES`, `METERS`, `FEET`). Values stay in the
  prescribed unit; clients convert for display only.

## API Endpoints

### Muscles (`/aldebaran/muscles`) and Movements (`/aldebaran/movements`)

* `GET /`, `GET /{id}`, `GET /reference-data`: catalog and form reference data. Movement summaries carry `componentNames` and `acceptedNames`; `reference-data` carries `exclusiveTechniqueSets`.
* `GET /usual-names`: every usual name (Power Clean = Clean + `POWER`), each one a movement with techniques and equipment; the movement search of the WOD forms.
* `GET /movements/{id}/variant?techniques=&equipment=`: one variant of a movement: usual name, description, video, implement count, variant key and adjusted roles.
* `GET /muscles/{id}/movements`: movements working a muscle (with its group or members), by role, with the technique or equipment that brings an adjusted role.
* `POST /`, `PUT /{id}`, `DELETE /{id}`: [ADMIN] manage the catalog.

### WODs (`/aldebaran/wods`)

* `GET /`: paginated `Slice` of WOD summaries the user can read. Optional filters, all combinable:
  `search` (title), `type` (format), `category`, `movementId`, `scope` (`ALL` default, `MINE`,
  `GYM`, `PUBLIC`),
  `gymId` (with `GYM`) and `authorId` (that author's personal WODs). Summaries include `authorId`,
  `gymId` and `isPublic`.
* `GET /` (list) also takes `structure` (sub-category) and returns, per summary, `structure` and
  `movementNames` (movement names in order, one extra query per page); `GET /count` counts the
  list for the same filters (numbered pagination of the catalogue).
* `GET /facets`: values available to filter the list, with the same security (plus `categoryCounts`
  and `total` for the search and scope, and `structures` present for the `type` given): `wodTypes` and
  `categories` present for the current `search` / `scope` / `gymId` / `authorId` (so the UI never
  offers an empty chip),
  plus `gymIds` and `authorIds` of readable WODs (admin selectors).
* `GET /{id}`: full WOD with its movements and `capabilities` (`canModify`, `locked`, `canJudge`:
  judge of a competition WOD) computed for the current user.
* `POST /{id}/copy` `{gymId, competition}`: copy a readable WOD into a physical gym (`WOD_WRITE` there;
  400 for a virtual programming) or as a personal WOD (`gymId` null), e.g. a public Open WOD copied as a gym competition WOD. The copy is
  private, owned by the caller, keeps `sourceWodId` and has its own scores and leaderboard.
* `POST /`, `PUT /{id}`, `DELETE /{id}`: manage a WOD (rules above). A session WOD answers 400
  (edited through its session); a WOD borrowed by a session block cannot be deleted (409).
* `GET /reference-data`: WOD formats, categories, score types and unit groups.
* `repMax` (former V18): N of an "N RM" test, only on an individual For Load WOD with one movement (400
  otherwise). Per line, `percentOfRm` (0-150) and `rmReps` (default 1 with a percentage, dropped
  without): the load as a percentage of the athlete's RM of the base version of the movement.

### Records (`/aldebaran/records`, lot C)

Lot M: records are stored at the most precise variant and read through the single "counts for" rule
(`service/VariantDominance`, `service/RecordInheritance`): a Power Clean counts for the Clean, a Clean and
Jerk for the Clean and the Jerk, a Jerk for Shoulder-to-Overhead. An entry another variant proves carries
`inheritedFrom`. `GET /me/progress` and `GET /gyms/{gymId}` take an optional `variantKey` (base version by
default); `GET /lifts` returns one test per movement, variant and N, with its usual name.

`movement_records` (former V18) holds each athlete's best load per movement, variant key and number of reps,
rebuilt by `MovementRecordService.rebuild(userId, movementId)` after every score write, correction or
deletion, and once at startup when the table is empty (`config/MovementRecordBackfill`).

* Sources (lot S, D144): individual WODs scored `WEIGHT`, `LOAD` or `NONE` (any format), every main
  line outside a complex: each complete set with a load on that line is a candidate for the N RM of its
  reps, for the movement and variant done on it (a Front Squat done in place of a Back Squat feeds the
  Front Squat); a score without sets on an "N RM" test (`repMax`, one line) is a candidate for N. Every
  scaling level and unverified scores count; team scores and complexes never do. On a tie the earliest
  day keeps the record.
* Variant key (`service/LiftVariant`): sorted equipment `;` sorted techniques of the WOD line, the
  implied equipment removed (`movements.implied_equipment`; for the Squat only with a load position),
  a component technique written `1:POWER`; `''` is the base version.
* `GET /me[?movementId=]`: the caller's records by movement (name order, with its `category`) and
  variant (base first, each with its `usualNameEn`), with `estimatedOneRepMaxKg` (Epley from the 2-10 rep records), `estimates`
  (the usual 1/2/3/5/10 RM without a record, Epley from the real 1RM else the estimated one) and, per
  record, `previousWeightKg` (best load before its day, former V19); estimates are displayed only, never stored.
* `GET /me/progress?movementId=&reps=1[&variantKey=]`: each day the caller set a new best N RM on that variant.
* `GET /lifts`: canonical lifts, i.e. public "N RM" tests (one per movement, variant and N, with the
  usual name, official competition events excluded: an Open 1RM still feeds the records but is never the
  lift's test); their WOD leaderboard is the lift leaderboard.
* `GET /gyms/{gymId}?movementId=[&variantKey=]`: records board of a box for that variant (base version
  by default): effective records of the gym's current usable members (Antares `member-ids`, 503 if unavailable), wherever set; members of the gym or admin.

### Weekly balance (`/aldebaran/balance`, lot D)

The athlete's own balance, from their scores and the session blocks checked in a journal without a score
(prescription). Facts only: no alert and no ratio (D89). Since lot S (rules version 3) it reads the lines as done: the
variant done (a substitute counts with its own muscles and modality), the reps and loads done, each line's
own sets, the reps counted on "max" lines; a line in calories counts on its men's value.

* `GET /me?week=yyyy-MM-dd` (any day of the week; Sirius sends the Monday of the athlete's local week): muscles,
  metcon minutes per time domain, strength sets per lift ("near the record" = a set at 80 % of the 1RM of its day)
  and modalities over the selected week and the 3 before, equivalent sets of the selected week and the 7 before.
* `POST /recompute` (`ADMIN`): every stored week to rebuild, `wods.modalities` recomputed (calibration).
* `POST /readable-texts {texts, wodType, scoreType, ladderStart, ladderStep, repMax}` (any user): one flag per line
  text, false when the balance cannot read it as that WOD counts it (D121: `21`, `21-15-9`, `max`, `1 min`,
  `3-6-9-...`, `5 x 5`; strength needs repetitions or the WOD's N RM; a Death by line climbs the ladder; blank = the
  line's measures). The WOD form warns, never blocks.
* `GET /uncounted-lines` (`ADMIN`): every WOD line that counts nothing (`UNREADABLE` text, or `NO_SET_VALUE` = no
  value of one set for its unit in the catalog; a strength WOD counts sets, so only its text is judged), rules
  `BalanceCalculator.readableText` / `uncounted`.

Storage (Flyway V7, D120): `balance_weeks` (per athlete and Monday: volume, modalities, seconds per domain,
`rules_version`, `stale`), `balance_week_muscles` (moving / holding sets per exact muscle), `balance_week_lifts`,
entities `BalanceWeek`, `BalanceWeekMuscle`, `BalanceWeekLift` and their repositories.
A shuttle line with a `length` (between the two lines, one rep = out and back) counts its meters against the
usual name's `meters_per_set` (Shuttle Run: 450 m = 30 reps of the usual 15 m out and back, V9); without a
length, its reps against `reps_per_set` (30, V8).
A week with nothing done and no row is never stored (any requested week writes nothing).
A write only marks weeks stale (`service/BalanceMaintenanceService`: score logged / corrected / re-dated /
deleted for the whole team, and every later week on a WOD feeding records; journal created / deleted / block
toggled; a checked WOD or session edited; a movement updated = every week); the display rebuilds the stale,
missing or older-rules weeks it needs (`WeeklyBalanceService`, advisory lock per athlete). Rules in
`service/BalanceCalculator` (`RULES_VERSION`; bump it when a rule changes):

* a line of `s` equivalent sets gives agonists `s`, synergists `s / 2` in movement and stabilizers `s` in holding
  (roles adjusted by the line's techniques); `s` = the line's amount ÷ the catalog's value of one set in its unit
  (`CatalogRules.perSet`, D126);
* metcons (`MetconVolume`, D121): readable prescription of a finished WOD; else the score unrolled in line order
  (AMRAP, capped For Time, open ladder; a distance line is worth the rest of `repsPerRound`); `max` lines share the
  score; else one set per minute of work; unreadable text counts 0. A team line `TOTAL` is divided by the smaller of
  the team size and the members (D128). Minutes outside Skill / Accessory blocks: For Time = the time (cap when
  capped), AMRAP = its duration, EMOM / Tabata / rests / team turns = intervals;
* strength (For Load, scored by load, or any score with sets, D122): completed sets, warm-ups under half the best set
  left out, a set of 1 to 5 reps worth half; one movement or one complex reads the athlete's sets, several lines
  the prescription.

### Programming balance (`/aldebaran/programming`, lot D')

`GET /programming/gyms/{gymId}/balance?start=&weeks=4&scaled=false&optional=false&days=`: the prescription of a
gym's CrossFit sessions over a cycle of 4 to 8 weeks, for its staff of any role (admin too; `canReorder` = WOD_WRITE).
Computed on request from the sessions, nothing stored (`ProgrammingBalanceService`; specialties and other disciplines
left out, same-day sessions merged, optional blocks only when asked, official Scaled versions with `scaled`).
Evaluated on each rolling window of 4 weeks for the full week and every profile of 3, 4 and 5 fixed days
(`ProgrammingBalance`), rules pure and unit tested in `ProgrammingAlerts` (decisions D173-D181, every constant
provisional until the beta):

* **alerts** (3 at most per profile): a pattern axis of a profile under 0.6 times its share in the coach's full week
  (relative to the coach, never a norm), judged only when the full-week share reaches 5 % and the axis is regular: an
  athlete coming the same number of days at random misses it less than 10 % of the time (`RandomAttendance`, exact
  count, no random draw); a main group (7) under 4 sets per week on average;
* **information**: regions against the full week (behind under 0.8 times, ahead over 1.25 times), axes concentrated
  on few days (an athlete without fixed days misses them, `concentratedAxes`), unusual sessions (a region over 1.3
  times its biggest day of the 4 programmed weeks before, at least 3 sets; `competition` when the day holds a
  competition block, such as an in-house throwdown, where such a volume is expected).

The response also counts the weeks holding a counted session (`programmedWeeks`: fewer than `weeks` means empty weeks
were read) and the lines with an official Scaled version (`scaledLines`: 0 means Scaled reads as RX), so Sirius can
warn the coach.

Never shown to an athlete (D89).

`POST /programming/gyms/{gymId}/order-proposals` (writers: `WOD_WRITE` or admin) proposes, for the same cycle and
switches, the order of each week's sessions that leaves the fewest profiles in alert (D168-D171), nothing written: the
WOD parts move (WOD, Accessory, Finisher) and the Skills stay, whole sessions move, or both move separately; past,
logged, scored and competition days never move, nor the blocks pinned in the request; when the Skills move, strength
days in a row never exceed the programmed ones (strict, or soft when no main group is agonist in both). Ties go to
fewer consecutive days in the same metcon domain or dominant modality, then to fewer moves. `POST .../order` applies
the weeks the coach keeps in one transaction (blocks re-attached to the week's sessions, or sessions' dates swapped),
after checking rights, locks (409) and that each week only swaps its own parts (400).

### Scores (`/aldebaran/scores`)

* `GET /me`: the current user's scores (optional `wodId`, paginated; with `sessionId` too, only the
  scores logged in that session's journal, e.g. a borrowed Fran in today's session).
* `POST /`, `PUT /{id}`, `DELETE /{id}`: log or edit a score (rules above). The value required by the
  WOD's score type must be present (400 otherwise); a time-capped TIME score stores the reps done at
  the cap and no time; a score never changes athlete or WOD (400 if `wodId` differs).
* Score types: `TIME` (time, or reps at the time cap), `ROUNDS` (rounds completed on the minute of an
  EMOM such as Chelsea, at most `emomRounds`), `REPS` (total reps; every AMRAP, as on
  games.crossfit.com), `WEIGHT` (one load: 1RM, load used for every set), `LOAD` (sum of loads, e.g.
  CrossFit Total), `CALORIES`, `DISTANCE`, `NONE`.
* AMRAP with a fixed round: the WOD's optional `repsPerRound` (Cindy 30; a calorie, a meter rowed or
  a 5-ft lunge section counts as a rep, as in the Open) lets clients enter and show the total as
  rounds + reps. A score sent as `rounds` + `reps` is stored as `rounds * repsPerRound + reps`
  (400 if the WOD has no fixed round). Ladders, chippers and rounds that differ for men and women
  (Open 18.1) have none. The former V7 migration converted the former `ROUNDS_REPS` WODs and scores.
* Tiebreak: a WOD may define its official tiebreak (`tiebreakEn` / `tiebreakFr`, e.g. Open
  workouts); a score's `tiebreakSeconds` is always optional, kept only for such WODs (and for a TIME
  WOD only when time-capped), and only separates equal scores in compare and leaderboards.
* Personal record: one per athlete and WOD, ordered by scaling level (Rx+ > Rx > Scaled > Custom),
  then performance; for TIME every finished time beats every capped score, capped scores rank by
  reps (`service/ScoreRanking`, shared with compare and leaderboards).
* `GET /{id}/compare`: rank and percentile of a score among the other athletes' PRs at its level.
* `GET /leaderboard/{id}`: leaderboard of a WOD (`scaling`, default `RX`): each athlete's PR at that
  level; optional `gymIds` (comma-separated, all among the caller's gyms, 403 otherwise) keeps the
  scores performed there (e.g. the caller's boxes, or programs), `verifiedOnly=true` the
  verified ones, `division` (`MEN`, `WOMEN`) the scores logged in that division, `ageGroup`
  (`AGE_15_AND_UNDER` ... `AGE_65_PLUS`, CrossFit Open brackets) the scores logged in that age group, and `sessionId` the
  scores logged in that session's journals (session leaderboard; the session content must be visible
  to the caller, `SessionSecurity.canReadContent`, 403 otherwise). Filters apply before choosing each athlete's best score: an athlete is ranked by
  their best kept score, which may not be their PR (done in another gym, or not verified yet).
  Filtered leaderboards are ranked in memory in `ScoreService`, the worldwide one in the database.
* Division (former V10, `scores.division`): copied at logging from `X-Auth-User-Division` for one's own
  score; for a score staff log for an athlete, the athlete's, read from Antares
  (`GET /antares/internal/users/{id}/division`, 503 if unavailable), never the judge's. A score logged
  with a division also classifies the athlete's older scores without one. A division change applies
  to the scores logged afterwards; scores without a division only appear on the "all" leaderboard.
* Age group (former V18, `scores.age_group`): age = year of the score − birth year, CrossFit Open brackets,
  computed at logging from `X-Auth-User-Birth-Year` (own score) or Antares' `birthYear` (staff score);
  the birth year is never stored. Older scores without a group get the group of their own year at the
  athlete's next score. Team scores have none.
* `POST /{id}/verify`, `DELETE /{id}/verify`: verify a submitted competition score or withdraw the
  verification (rules above; 400 outside a competition WOD or for a score not submitted).
* `GET /verification-queue/{wodId}`: submitted scores of a competition WOD not verified yet, oldest
  first (judges: `SCORE_VERIFY` in its gym).
* Session fields: `sessionLogId` (journal), `sets` (strength sets), `verificationRequested`.
  Responses carry user IDs only: clients resolve names with Antares `POST /users/display-names`.
* `GET /reference-data`: scaling levels.

### Sessions (`/aldebaran/sessions`, `/aldebaran/session-logs`, `/aldebaran/gyms/{gymId}/session-settings`)

* `GET /sessions`: frames of the readable sessions by date (`scope` `ALL` / `MINE` / `GYM`, `gymId`,
  `from`, `to`: a specialty covers its week, `type`), with `contentVisible` and `loggedByMe`. A
  platform admin's lists keep their own gyms and personal sessions; another gym only with its
  `gymId` (the admin still opens any session by id).
* `GET /sessions/{id}`: frame, content when visible, `capabilities {canModify, canDelete, contentVisible,
  canLog}`, blocks with `capabilities {canModify, canDelete, frozen}`, and `myLog`.
* `POST /sessions`, `PUT /sessions/{id}` (blocks merged by `id`; an owned WOD left behind is deleted),
  `DELETE /sessions/{id}` (deletes its owned WODs), `PUT /sessions/{id}/visibility`.
* `POST /sessions/{id}/logs` `{date?, gymId?}`: "Séance faite". `GET /session-logs/me` (`from`, `to`),
  `PUT /session-logs/{id}/blocks/{blockId}` `{done}`, `DELETE /session-logs/{id}`.
* `GET` / `PUT /gyms/{gymId}/session-settings` `{defaultVisibility, timeZone}`: members read,
  `MANAGE_SETTINGS` writes.
* `GET /sessions/reference-data`: types, disciplines, block kinds, stimuli, visibility policies.

### Export (`/aldebaran/export`) — temporary, development only

* `POST /muscles`, `POST /movements`: [ADMIN] write the current catalog to
  `src/main/resources/muscles.csv` / `movements.csv` (through the source volume mounted in
  `docker-compose.yml`).
* `POST /wods`: [ADMIN] write the public WODs to `src/main/resources/wods.csv` /
  `wod_movements.csv`.

## Catalog Backup (Development)

While the software is being developed, muscles, movements and public reference WODs (Girls, Heroes,
CrossFit benchmarks, Open, Quarterfinals, Semifinals, Games) are edited in the Sirius UI, then exported to CSV with the endpoints above. The CSV
files are a local backup of the latest catalog: if the database is reset, `config/DataSeeder`
reloads muscles and movements into empty tables, then the WODs when no public WOD exists (public,
no author, no gym). The CSV files are git-ignored (never pushed); on a fresh clone the catalog
starts empty. This mechanism (export endpoints, source volume, CSV seeding) is temporary.

* `muscles.csv`: `medical_name,region,parent,common_name_en,common_name_fr,description_en,description_fr,image_url,joint_actions,code`
  (`parent` = medical name of the functional group, linked once every muscle is saved; `code` keeps the 3D model link
  of a renamed muscle, derived from the name when empty).
* `movements.csv`: equipment, techniques and muscles (`agonists`, `synergists`, `stabilizers`) are
  lists separated by `, `.
* `wods.csv`: `title,wod_type,category,score_type,rep_scheme,time_cap_seconds,emom_interval,emom_rounds,description_en,description_fr,notes_en,notes_fr,tiebreak_en,tiebreak_fr,reps_per_round,rounds,rest_seconds,work_seconds,ladder_start,ladder_step,vest_weight,vest_weight_female,vest_weight_unit,structure` (from `tiebreak_en` on optional; former `wod_type` values read as a category + sub-category; a former `ROUNDS_REPS` score type reads as `REPS`)
* `wod_movements.csv`: `wod_title,order_index,movement_name,reps_scheme,weight,weight_unit,distance,distance_unit,duration_seconds,calories,notes_en,scaling_options_en`,
  plus optional `duration_display_unit,equipment,techniques,weight_female,notes_fr,scaling_options_fr,calories_female,height,height_female,height_unit,sets,segment,complex_group` (blank segment = `MAIN`)
  (lists separated by `|`). Files with the former `description`, `notes` and `scaling_options`
  columns still load (read as English).
  `movement_name` is the exact movement name; an unknown one is logged and the row skipped.
  Blank cell = `null`, so the export → seed round-trip is lossless (`config/WodCsvCodec`).

With the `demo` Spring profile, `demo/DevGymWodSeeder` (package `aldebaran.demo` and resource `demo/programs.json`,
removable as a whole) writes two original demo programs through the services (`demo/DemoPrograms`), centered on the seed
day (6 weeks before, the seed week, 5 after): a balanced 12-week programming for the box "Spartacus CrossFit" (author
`owner.sparta@demo.test`, Monday to Saturday, official Scaled version on every WOD, box copies of the benchmarks, team
WOD every Saturday, an in-house throwdown of 4 events) and an unbalanced one for the program "Zeus Programming" (author
`programmer.zeus@demo.test`, 6 days, fixed themes per day: many profiles in alert on the coach's programming balance),
plus a personal WOD for `athlete.sparta@demo.test`. `demo/DemoProgramScores` then logs the past sessions of each demo
athlete (own days and level, journals, Skill and WOD scores, Scaled, teams, judges and the verification queue). The
seeders act as the demo account they write for (`demo/DemoActor`: an in-memory security context on the seeding thread,
built from the Antares tables like the gateway headers), so every rule of the services applies. IDs are read from the
`antares` schema of the same database, retried in the background for about 60 s; each program is skipped when its gym
already has a session with its title prefix, its scores when a journal exists. The design and the export script live in
`docs-private/demo` (`progs-demo-sparta-zeus.md`, `outils/export_json.py`). `demo/DevScoreSeeder` logs, through
`ScoreService` as each athlete, scores on the public benchmarks (Fran, Chelsea, Cindy, Grace, Murph, Open 25.1/25.2,
CrossFit Total) and the personal WOD (skipped once the owner has a score on the public Fran), then about 45 scores on
the public "N RM" tests (Back Squat, Deadlift, Clean and Jerk, Snatch, Shoulder Press, Bench Press, Front Squat), so the
records, lift leaderboards and box board have data (skipped once the owner has a score on "Back Squat 1RM"). Division,
age group, personal records and records come from the service. The demo seeders read the Antares schema with
`JdbcTemplate` (users, gyms, memberships: another schema) and their own tables through repositories.

## API Documentation

The OpenAPI document is served at `/aldebaran-docs`. Swagger UI is disabled here
(`springdoc.swagger-ui.enabled=false`): the `bellatrix-swagger` service provides a centralized
Swagger UI at https://docs.stellar.apex (admin only).
