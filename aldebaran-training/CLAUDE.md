# Aldebaran (training API)

Spring Boot 4.1 / Java 25 service, base path `/aldebaran`, port 8081, schema `aldebaran` (Flyway).
Domain: muscles, movements, WODs (`Wod` + `WodMovement`) and scores.
Dev-only catalog backup (temporary, git-ignored CSVs, see root `CLAUDE.md`): `controller/Exporter`
(admin only) writes `src/main/resources/{muscles,movements,wods,wod_movements}.csv` through the source
volume mounted in `docker-compose.yml`; `config/DataSeeder` reloads them into empty tables (WODs:
when no public WOD exists). `muscles.csv` has a `parent` column (functional group, by medical name).
WOD CSV format lives in `config/WodCsvCodec` (blank = null, lossless round-trip; movements by exact
name; WOD movement variants joined by `|`, whereas `movements.csv` lists use `, `; FR/EN text
columns `*_en` / `*_fr`, the former single-language columns still read as English; optional trailing
`tiebreak_en` / `tiebreak_fr` / `reps_per_round`; a former `ROUNDS_REPS` score type reads as `REPS`).
`config/DevGymWodSeeder` (`@Profile("dev")`, temporary) seeds WODs for the Antares test gyms,
resolving IDs from the `antares` schema with `JdbcTemplate`, then calls `config/DevScoreSeeder`
(scores of the test accounts on gym WODs and public benchmarks, PRs via `ScoreRanking`; skipped when
`owner@sparta.com` already has scores).
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
  `GET /wods/facets` queries: change it once.
- Per-user flags for the UI (`WodCapabilities`, `ScoreCapabilities`) are computed by
  `service/WodCapabilityService` / `ScoreCapabilityService` per request, never inside cached methods
  (`WodService.getWodDetail` is `@Cacheable`). `ScoreCapabilityService.forViewer` also hides others'
  comments/scaling notes from non-moderators.
- Score ordering lives once in `service/ScoreRanking` (PR, compare, leaderboard sort); score/WOD
  consistency in `ScoreService.normalizeAndCheck` (`InvalidScoreException` → 400).
- Antares facts Aldebaran cannot know (another user's membership) come from `client/AntaresClient`
  (`application.antares.url`, internal API with `X-Internal-Secret`, 3 s timeout). Any failure throws
  `AntaresUnavailableException` (503): never authorize by default. ITs mock it in
  `BaseIntegrationTest` (`antaresClient`).
- Competition WODs: `ScoreSecurity.canCreate` (staff logging), `canModify` (verified score locked for
  its athlete), `canVerify`; `WodSecurity.canJudge` feeds `WodCapabilities.canJudge`;
  `WodService.checkCompetition` (gym required, never public, `InvalidWodException` → 400).
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
- Catalog rules: `WodMovement.checkVariantsAllowed` (variants allowed by the movement; used by
  `WodService`, `WodCsvCodec`, `DevGymWodSeeder`; `InvalidWodMovementException` → 400), its
  converse in `MovementService.updateMovement` (a variant used by any WOD cannot be removed;
  `WodRepository.countByMovementEquipment/Technique`; 409 `error.movement.variant.in.use`) and
  `MuscleService.applyParent` (one-level functional groups, same region; 409).
- Errors: throw domain exceptions with a message key (`ResourceNotFoundException("error.wod.not.found", id)`,
  `WodLockedException` → 409); `exception/GlobalExceptionHandler` turns them into localized
  `ProblemDetail`. Add new keys to `messages.properties` and `messages_fr.properties`.
- Tests: `*Test` = unit (JUnit 5 + Mockito, principals from `security/TestPrincipals`); `*IT` extends
  `config/BaseIntegrationTest` (full context, random port, Testcontainers).
