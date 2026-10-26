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
start PostgreSQL and Redis with Testcontainers: Docker must be running.

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
* **Scores**: athletes manage their own; `SCORE_VERIFY` on the WOD's gym allows managing other
  athletes' scores.
* **Muscles / movements**: read by everyone, written (and exported to CSV) by platform ADMIN only.
* A platform ADMIN can do everything.

Rules live in `security/WodSecurity` and `security/ScoreSecurity`, used from `@PreAuthorize`.

## Catalog Model

* **Muscles**: a region (`muscleGroup`: `LEGS`, `BACK`, `CHEST`, `SHOULDERS`, `ARMS`, `CORE`) and an
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
* **WOD format and category**: `wodType` is the format (`FOR_TIME`, `AMRAP`, `EMOM`, `TABATA`,
  `INTERVALS`, `STRENGTH`, `SKILL`, `ACCESSORY`, `CUSTOM`); the optional `category` is the family
  (`GIRLS`, `HERO`, `BENCHMARK` = official CrossFit benchmarks such as Fight Gone Bad,
  `CUSTOM_BENCHMARK` = a gym's flagship WODs, `OPEN`, `QUARTERFINALS`, `SEMIFINALS`, `GAMES`).
  Fran is a `FOR_TIME` WOD of the `GIRLS` category.
* **WOD texts and loads**: description and notes, and each WOD movement's notes and scaling options,
  exist in English and French (`*En` / `*Fr`, either may be empty; clients fall back on the other
  language). A WOD movement holds the men's load (`weight`) and the women's load (`weightFemale`) in
  the same unit; clients convert for display only.

## API Endpoints

### Muscles (`/aldebaran/muscles`) and Movements (`/aldebaran/movements`)

* `GET /`, `GET /{id}`, `GET /reference-data`: catalog and form reference data.
* `POST /`, `PUT /{id}`, `DELETE /{id}`: [ADMIN] manage the catalog.

### WODs (`/aldebaran/wods`)

* `GET /`: paginated `Slice` of WOD summaries the user can read. Optional filters, all combinable:
  `search` (title), `type` (format), `category`, `movementId`, `scope` (`ALL` default, `MINE`,
  `GYM`, `PUBLIC`),
  `gymId` (with `GYM`) and `authorId` (that author's personal WODs). Summaries include `authorId`,
  `gymId` and `isPublic`.
* `GET /facets`: values available to filter the list, with the same security: `wodTypes` and
  `categories` present for the current `search` / `scope` / `gymId` / `authorId` (so the UI never
  offers an empty chip),
  plus `gymIds` and `authorIds` of readable WODs (admin selectors).
* `GET /{id}`: full WOD with its movements and `capabilities` (`canModify`, `locked`) computed for
  the current user.
* `POST /`, `PUT /{id}`, `DELETE /{id}`: manage a WOD (rules above).
* `GET /reference-data`: WOD formats, categories, score types and unit groups.

### Scores (`/aldebaran/scores`)

* `GET /me`: the current user's scores (optional `wodId`, paginated).
* `POST /`, `PUT /{id}`, `DELETE /{id}`: log or edit a score (rules above).
* `GET /{id}/compare`: rank and percentile of a score.
* `GET /leaderboard/{id}`: leaderboard of a WOD (`scaling`, default `RX`).
* `GET /reference-data`: scaling levels.

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

* `muscles.csv`: `medical_name,muscle_group,parent,common_name_en,common_name_fr,description_en,description_fr,image_url`
  (`parent` = medical name of the functional group, linked once every muscle is saved).
* `movements.csv`: equipment, techniques and muscles (`agonists`, `synergists`, `stabilizers`) are
  lists separated by `, `.
* `wods.csv`: `title,wod_type,category,score_type,rep_scheme,time_cap_seconds,emom_interval,emom_rounds,description_en,description_fr,notes_en,notes_fr`
* `wod_movements.csv`: `wod_title,order_index,movement_name,reps_scheme,weight,weight_unit,distance,distance_unit,duration_seconds,calories,notes_en,scaling_options_en`,
  plus optional `duration_display_unit,equipment,techniques,weight_female,notes_fr,scaling_options_fr`
  (lists separated by `|`). Files with the former `description`, `notes` and `scaling_options`
  columns still load (read as English).
  `movement_name` is the exact movement name; an unknown one is logged and the row skipped.
  Blank cell = `null`, so the export → seed round-trip is lossless (`config/WodCsvCodec`).

With the `dev` Spring profile, `config/DevGymWodSeeder` (temporary) also creates private WODs for
the Antares test gyms "Spartacus CrossFit" (author `owner@sparta.com`) and "Zeus Programming"
(author `programmer@zeus.com`), plus a personal WOD for `athlete@sparta.com`. IDs are read from the
`antares` schema of the same database, retried in the background for about 60 s; skipped if Sparta
already has WODs.

## API Documentation

The OpenAPI document is served at `/aldebaran-docs`. Swagger UI is disabled here
(`springdoc.swagger-ui.enabled=false`): the `bellatrix-swagger` service provides a centralized
Swagger UI at https://docs.stellar.apex (admin only).
