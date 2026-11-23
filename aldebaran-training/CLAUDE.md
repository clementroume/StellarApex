# Aldebaran (training API)

Spring Boot 4.1 / Java 25 service, base path `/aldebaran`, port 8081, schema `aldebaran` (Flyway).
Domain: muscles, movements, WODs (`Wod` + `WodMovement`), scores and sessions (`Session` +
`SessionBlock`, journal `SessionLog`, lot B; model and rules in `README.md` § Sessions).
Catalog (lot E): never in git. `tools/catalog/generate_catalog.py` freezes the local catalog into private repeatable
migrations `src/main/resources/db/catalog/R__*.sql` (git-ignored, `spring.flyway.locations` includes `db/catalog`;
upsert by stable code `muscles.code` / `movements.code` / `wods.code`, never a deletion, WOD lines updated in place by
(WOD code, order); a removal is a hand-written `V` migration). Only a catalog WOD (public, no gym, created or published
by an admin: `WodService.assignCatalogCode`) has a code. ITs load `db/migration` only. `config/CatalogCachePurge` empties
the catalog caches at startup; Redis keys carry `application.cache.key-prefix`.
`demo/DevGymWodSeeder` (`@Profile("demo")`, package `aldebaran.demo` + resource `demo/programs.json` = what to delete to
retire the demo) resolves the Antares gyms and users with `JdbcTemplate`, writes the two demo programs (`DemoPrograms`:
sessions through `SessionService` / `WodService`, data exported from `docs-private/demo/outils/export_json.py`), then
`DevScoreSeeder` (public benchmarks, N RM tests, personal WOD) and `DemoProgramScores` (journals and scores of the past
sessions through `ScoreService`). The demo seeders act as the demo account they write for (`DemoActor`, in-memory
security context on the seeding thread, demo profile only; decision of 2026-11-19): never call a service as another user
anywhere else.
`./mvnw verify` runs unit tests and `*IT` (Testcontainers PostgreSQL + Redis, Docker required).

## Identity and security

- No login here: `config/SecurityConfig` trusts the `X-Auth-*` headers set by Antares through Traefik
  (checked with `X-Internal-Secret`, constant time, empty secret rejects all) and builds `security/AldebaranUserPrincipal`
  (`platformRole`, `memberships: Map<gymId, GymAccess(role, permissions)>`, `actorId`).
  Header contract: see root `CLAUDE.md`.
- `security/SecurityService`: `isAdmin`, `hasGymPermission(principal, gymId, perm)`,
  `getCurrentUserMemberGymIds()` (returns `{-1}` when empty so JPQL `IN` stays valid; used in SpEL
  inside repository queries).
- Resource rules live in security beans used from `@PreAuthorize` (`@wodSecurity.canModify(#id, principal)`,
  `@scoreSecurity...`). Convention: when the resource does not exist, return `true` so the service
  throws the proper 404 instead of a 403.
- List filters live in one JPQL constant, `WodRepository.SECURE_FILTERS`, shared by the list and the
  `GET /wods/facets` queries: change it once. It keeps `standalone` WODs only (session WODs are read
  through their session).
- Sessions: `security/SessionSecurity` (frame = gym members; content = staff of any role, or per
  `VisibilityPolicy` / participation; `canReadWodContent` gates session WODs in `WodSecurity.canRead`
  and `ScoreSecurity.canCreate`). Time-based rules use the `Clock` bean (`config/ClockConfig`, UTC)
  and the gym's zone (`SessionSettingsService.zoneOf`). Session WODs are created / updated / deleted
  only through `WodService.*SessionWod` from `SessionService`. Lock per block
  (`SessionService.BlockLocks`, from `scores.session_block_id` set by `ScoreService.attachToSession`, `WodLockedException` → 409): scored block frozen, checked block
  undeletable but editable, date / type / deletion locked once a journal exists. `@PastOrPresent` dates (scores, journals) are checked against the
  application clock shifted to UTC+14 (`config/ValidationClockConfig`): clients send the athlete's local
  day, the server runs in UTC. PostgreSQL cannot type a null date parameter: pass
  `SessionRepository.NO_START` / `NO_END` instead of null.
- Per-user flags for the UI (`WodCapabilities`, `ScoreCapabilities`) are computed by
  `service/WodCapabilityService` / `ScoreCapabilityService` per request, never inside cached methods
  (`WodService.getWodDetail` is `@Cacheable`). `ScoreCapabilityService.forViewer` also hides others'
  comments from non-moderators.
- Team scores (`ScoreTeam`, `ScoreService.teammatesOf` / `formTeam`): one row per member, kept in
  sync on update; team WOD leaderboards go through the in-memory path keyed by team.
- Score ordering lives once in `service/ScoreRanking` (PR, compare, leaderboard sort; `Score.tier()` = RX >
  official Scaled > free Scaled); score/WOD consistency in `ScoreService.normalizeAndCheck` (`InvalidScoreException` → 400).
- Score entry (lot S, `docs-private/roadmap/lots-passation-saisie-scores.md` D140-D154): levels RX / SCALED only;
  `score_lines` = lines done otherwise than prescribed (variant + quantities, load in kg), `score_sets.wod_movement_id`
  (a complex: its first line), `wod_scaled_lines` = the WOD's official Scaled version (`WodMovement.getScaled()`).
  `service/ScoreLineService` builds them (rows updated in place: unique per score and index / line) and derives
  `LOAD` = sum of each line's best set; `ScoreConformity` (pure) refuses a changed RX (400), sets `rxPlus` (never
  ranked) and `scaledOfficial` (Scaled leaderboard), also answered without saving by `POST /scores/level-preview`
  (`ScoreService.previewLevel`, readers of the WOD); `LineVersions` (pure) builds the scaled line and the line as
  done, read by the display, records (`LiftVariant.lifts`: every strength line outside a complex, the variant done)
  and the weekly balance (`Workout.lines()` / `maxCounts()`). `UnitEquivalence` (pure, D149) = box values between
  kg / lb and in / cm (exact in competitions), sent as `WodMovementResponse.equivalents` and used for RX.
  Units: performances in kg / m / s, prescriptions in their unit of origin; `@UnitOf` + `CHECK` per unit column.
- Antares facts Aldebaran cannot know (another user's membership) come from `client/AntaresClient`
  (`application.antares.url`, internal API with `X-Internal-Secret`, 3 s timeout). Any failure throws
  `AntaresUnavailableException` (503): never authorize by default. ITs mock it in
  `BaseIntegrationTest` (`antaresClient`).
- Competition WODs: `ScoreSecurity.canCreate` (staff logging), `canModify` (verified score locked for
  its athlete), `canVerify` (submitted scores only: `Score.verificationRequested`); `WodSecurity.canJudge` feeds `WodCapabilities.canJudge`;
  `WodService.checkCompetition` (gym required, never public, `InvalidWodException` → 400).
- Division: `AldebaranUserPrincipal.division` (`X-Auth-User-Division`); `ScoreService.logScore` copies
  it on own scores, or `AntaresClient.getAthleteFacts` for a score logged for someone else (503 when
  Antares fails, never the judge's division), then `assignDivisionToUnclassified`. Leaderboard filter
  `division` goes through the in-memory filtered path (`findFilteredByWod`).
- Age group: `AldebaranUserPrincipal.birthYear` (`X-Auth-User-Birth-Year`, never stored) or
  `AntaresClient.getAthleteFacts` for a staff score → `scores.age_group` (`model/enums/AgeGroup`), then
  `assignAgeGroupToUnclassified`; team scores keep none. ITs default `getAthleteFacts` to no facts in
  `BaseIntegrationTest`.
- Records (lot C): `service/MovementRecordService` rebuilds `movement_records` for (athlete, movement)
  from `ScoreService` after each log / update / delete (`rebuildFor(wod, userId)`), never from a
  request; `service/LiftVariant` says whether a WOD feeds records and builds the variant key. Records
  are stored at the most precise variant and read through `service/RecordInheritance` (best of what
  counts for the slot, with `inheritedFrom`). The canonical "N RM" WODs live in the catalog migrations
  (`rep_max`), one per movement, variant and N.
- Weekly balance (lot D): own balance only (`GET /balance/me`). Every write that changes what an athlete did calls
  `service/BalanceMaintenanceService` (mark weeks stale, never compute in the write); the display rebuilds
  (`WeeklyBalanceService`). Rules live in `BalanceCalculator` / `MetconVolume` / `LinePrescription` (pure, unit
  tested); bump `BalanceCalculator.RULES_VERSION` when one changes. Tables `balance_*` = entities `BalanceWeek*`
  and their repositories (rebuild lock: its own advisory key, hashed with "balance", apart from the records' lock;
  an empty week never stored is not written); stale marks never clear the persistence context (they run inside score and journal
  writes), the rebuild's upsert does. Native queries name the schema (`aldebaran.`). No alert, no ratio (D89).
  Contract for lot D': `BalanceCalculator.vectorOf` (workouts done or prescribed → vector) and `sum` (linear).
  Lines that count nothing (D121 rule 5): `BalanceCalculator.readableText` (WOD form warning, `POST /balance/readable-texts`)
  and `uncounted` (admin control `GET /balance/uncounted-lines`, `model/enums/UncountedReason`).
- Programming balance (lot D', handover `docs-private/roadmap/lotd-prime-passation-programmation.md` D160-D181):
  `GET /programming/gyms/{gymId}/balance` (staff, `SessionSecurity.canReadProgramming`), computed on request by
  `ProgrammingBalanceService` from the sessions' prescriptions (`Workout.prescribed`, `BalanceCalculator`); pure rules in
  `ProgrammingBalance` (windows, profiles), `ProgrammingAlerts` (pattern gap relative to the full week, group minimum;
  regions, concentrated axes and unusual sessions as information) and `RandomAttendance` (exact regularity count).
  `WeekTotals.axes()` / `patterns()` = sets per pattern axis / detail (a line counts once per axis).
  Session order (PR 2, D168-D171): `SessionOrder` (pure: exhaustive search per week, windows not touched reused,
  candidates in parallel; score = profiles in alert, excess, same domain, moves; strength days in a row never more than
  programmed) behind `SessionOrderService` (`POST .../order-proposals` / `.../order`, `canReorder`); the application
  moves blocks with `SessionBlockRepository.moveTo` (an update, never an orphan removal; order constraint deferred) or
  dates with `SessionService.moveDate`. Modes and strength option: `model/enums/OrderMode`, `HeavyDays`.
- Support session: `X-Auth-Actor-Id` (nullable `actorId`) = the admin acting as the user; rights stay
  the user's. `security/SupportSessionAuditFilter` logs every non-GET request with an actor at INFO.

## Code conventions

- Layers: `controller` (thin, `ResponseEntity`, Javadoc on each endpoint) → `service`
  (`@Transactional`, business rules, cache eviction) → `repository` (Spring Data, JPQL with
  `...Secure` security clauses, `projection` interfaces for lists returned as `Slice`).
- DTOs are Java records in `dto` (`*Request` with Bean Validation using `{validation.*}` message keys,
  `*Response`, `*Summary*`); MapStruct mappers in `mapper` (`unmappedTargetPolicy = IGNORE`).
- Lombok (`@RequiredArgsConstructor`, `@Slf4j`, builders on entities). Javadoc on public classes and
  methods, 2-space indentation, Google Java style.
- Catalog model (lot M): movement + equipment + typed techniques. `service/VariantDominance` = the
  single "counts for" rule (exhaustive test: add every new technique there). `service/CatalogRules` =
  line normalization and checks (one written form, `Technique.exclusiveSets`, required values, component
  techniques), `display` (usual name + remaining words, sent as `WodMovementResponse.display`),
  `effectiveRoles` (adjustments, lowest role wins; for lot D), `perSet` (value of one set by unit, usual name then
  movement, D126), `patterns` (movement patterns, same lookup, then a chain's components; lot D' D177), `modality` / `modalities` (per line: usual name, family, load; also `wods.modalities`, D127), `checkMovement` (no cycle, no muscle with
  its group, roles against the joint layer).
- Catalog rules: `WodMovement.checkVariantsAllowed` (variants allowed by the movement; used by
  `CatalogRules`, `DevGymWodSeeder`; `InvalidWodMovementException` → 400), its
  converse in `MovementService.updateMovement` (a variant used by any WOD cannot be removed;
  `WodRepository.countByMovementEquipment/Technique`; 409 `error.movement.variant.in.use`) and
  `MuscleService.applyParent` (one-level functional groups, same region; 409).
- Errors: throw domain exceptions with a message key (`ResourceNotFoundException("error.wod.not.found", id)`,
  `WodLockedException` → 409); `exception/GlobalExceptionHandler` turns them into localized
  `ProblemDetail`. Add new keys to `messages.properties` and `messages_fr.properties`.
- Tests: `*Test` = unit (JUnit 5 + Mockito, principals from `security/TestPrincipals`); `*IT` extends
  `config/BaseIntegrationTest` (full context, random port, Testcontainers; one JVM and one context
  shared by all ITs, tables truncated before each test: create your own data, never depend on order,
  no per-class `@MockitoBean` except where unavoidable).
