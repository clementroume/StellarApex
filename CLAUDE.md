# StellarApex

Web app for CrossFit athletes and gyms (WODs, movements, muscles, scores, gym memberships).
Monorepo: one folder per service, each with its own build. Service-specific guidance lives in the
`CLAUDE.md` of each service folder.

| Folder               | Name      | Role                                                          | Stack                                  |
|----------------------|-----------|---------------------------------------------------------------|----------------------------------------|
| `antares-auth`       | Antares   | Auth (JWT in HttpOnly cookies), users, gyms, memberships; Traefik forward-auth | Java 25, Spring Boot 4.1, PostgreSQL, Redis |
| `aldebaran-training` | Aldebaran | Sport domain: muscles, movements, WODs, scores               | Java 25, Spring Boot 4.1, PostgreSQL   |
| `bellatrix-swagger`  | Bellatrix | Swagger/OpenAPI aggregator                                    | Spring Boot                            |
| `sirius-app`         | Sirius    | Frontend                                                      | Angular 22, Tailwind 4, spartan/ui, Vitest |
| `dynamic/`           | Altair    | Traefik dynamic config (routers, middlewares, forward-auth)   | Traefik 3                              |

Infra: `docker-compose.yml` (Traefik `altair-proxy`, `castor-db` PostgreSQL 18, `pollux-cache` Redis 8),
`docker-compose.observability.yml` (Grafana/Prometheus/Loki/Alloy). Public host: `https://stellar.apex`
(`/antares`, `/aldebaran`), docs on `docs.stellar.apex`. Human docs: one `README.md` per service plus
the root one; keep them in sync with code changes.

## Commands

```bash
cd sirius-app && npm test          # Vitest via ng test (non-watch)
cd sirius-app && npm run build     # production build (CI runs this)
cd sirius-app && npm start         # dev server on :4200, proxies /antares and /aldebaran to the local Traefik (Host: stellar.apex)
cd antares-auth && ./mvnw verify   # unit tests + *IT (Failsafe, Testcontainers: Docker must run)
cd aldebaran-training && ./mvnw verify
```

CI (`.github/workflows/ci.yml`) runs `npm test` + production build for Sirius and `./mvnw -B verify`
per backend, filtered by changed paths. Always run the relevant suite before calling work done.

## Rights model (most important cross-service contract)

Rules are enforced by the backends; Sirius only adapts the UI to what the backend sends. Never
re-implement a backend rule in the frontend when the backend can expose it.

Request flow: browser → Traefik → `forward-auth-api` middleware calls Antares
`GET /antares/auth/verify/api` (`AuthenticationController.verifyApi` → `GatewayIdentityService`) →
Antares returns identity headers → Traefik copies them onto the request to Aldebaran.

Headers Antares → Aldebaran (all listed in `dynamic/routes.yml` `authResponseHeaders`, which also makes
Traefik strip client-forged values; any new header MUST be added there):

- `X-Auth-User-Id`, `X-Auth-User-Locale`, `X-Internal-Secret`
- `X-Auth-User-Role`: platform role only (`ADMIN` | `USER`), never a gym role
- `X-Auth-Memberships`: every usable membership, `gymId:ROLE:PERM+PERM` joined by `,`
  (e.g. `12:COACH:WOD_WRITE+SCORE_VERIFY,15:ATHLETE:`). Usable = membership ACTIVE and
  (gym ACTIVE, or gym PENDING_APPROVAL and role OWNER/PROGRAMMER); `MembershipSummary.usable` tells Sirius
- `X-Auth-User-Division`: `MEN` | `WOMEN` | empty (athlete profile); Aldebaran copies it on each score
  the user logs (`scores.division`); a score staff log for an athlete takes the athlete's division
  from Antares `GET /antares/internal/users/{id}/division`, never the judge's. A division change applies
  to the next scores (older null-division scores are classified at the athlete's next score)
- `X-Auth-Actor-Id`: admin id, only during a support session (the other headers then describe the
  target user)

Effective permissions (single rule, `Membership.effectivePermissions` in Antares): OWNER/PROGRAMMER =
all, COACH = granted ones, ATHLETE = none.

Gym roles by type: physical gym = exactly 1 OWNER + COACH* + ATHLETE*; virtual gym (`isProgramming`)
= exactly 1 PROGRAMMER + ATHLETE*. OWNER/PROGRAMMER change only through
`POST /antares/gyms/{gymId}/transfer-ownership`; only they (or an admin) transfer or delete the gym.

Gym lifecycle (Antares): any user requests a gym (`PENDING_APPROVAL`, one waiting request per user,
admin approves / rejects / suspends). Members join an ACTIVE gym with its code (PENDING, or ACTIVE
with auto-subscription), leave or are removed as INACTIVE (row, history and scores kept; joining
again reactivates it), a refused request is deleted, BANNED blocks new requests. Membership management
rules live in `MembershipService.capabilities` (holder/admin: everything on others; COACH with
`MANAGE_MEMBERSHIPS`: status of athletes, no ban) and reach Sirius as `MembershipResponse.capabilities`.
Permissions: `WOD_WRITE`, `SCORE_VERIFY`, `MANAGE_MEMBERSHIPS`, `MANAGE_SETTINGS`.

Rights are evaluated on the gym of the resource (a user can belong to several gyms, e.g. a physical box
and a virtual programming); there is no "active gym" in requests.

- WOD read: public, or WOD gym in the user's gyms, or author. WOD create/update/delete in a gym:
  `WOD_WRITE` on that gym; personal WOD (`gymId = null`): author only. Admin: everything.
- WOD with logged scores is locked (409 on update/delete). Ownership (`gymId`, `authorId`) is ignored
  on update (`WodMapper.updateEntity`); `isPublic` stays editable.
- Scores: self-logging on any readable WOD; a score records the gym where it was performed
  (`score.gymId`: the WOD's gym, else one of the athlete's gyms); correcting/deleting others' scores:
  `SCORE_VERIFY` on that gym. Leaderboards return user IDs; names come from Antares
  `POST /antares/users/display-names` (gym mates, opted-in users, oneself).
  `ScoreResponse.capabilities {canModify, canVerify}`; others' comments hidden from non-moderators.
  Movements/muscles CRUD and CSV export: `hasRole('ADMIN')`.
- Competition WODs (`wods.competition`, physical gym WODs only, never public; a virtual programming
  sells its own WODs: no competition, no copies into it, only moderation of aberrant scores), the
  only place scores are
  verified: `SCORE_VERIFY` staff of the gym log scores for its members (logged = verified at once;
  Antares confirms the membership through its internal API, 503 if unavailable, never authorized by
  default) and verify/unverify the scores athletes submitted (`verificationRequested`; others stay plain
  scores; `POST|DELETE /scores/{id}/verify`, queue `GET /scores/verification-queue/{wodId}`), never
  their own. A verified
  score is locked for its athlete, staff included. Leaderboard filters (`gymIds`, `verifiedOnly`)
  apply before each athlete's best score is chosen, so a PR done elsewhere or not verified never
  hides a kept score (Sirius defaults `verifiedOnly` off for judges so they see the scores to
  verify). Logging
  for someone else elsewhere: admin only. `POST /wods/{id}/copy {gymId, competition}` copies a readable
  WOD (e.g. a public Open WOD) into a physical gym (`WOD_WRITE`), private, with `sourceWodId`; scores and
  leaderboards stay per WOD (a copy's scores never reach the public WOD).
- Sessions (lot B, Aldebaran `sessions`): a session is dated (WOD session = a day, several slots;
  specialty = a week) and holds ordered blocks: Skill + WOD required for a WOD session, optional
  Warm-up / Cool Down (text), Accessory, Finisher. A scored block owns a WOD (`standalone = false`,
  hidden from WOD lists, edited only through its session) or borrows a standalone WOD (scores join
  its leaderboard). Frame (date, type, discipline, block kinds and themes): members of the gym. Content:
  staff of any role (Owner/Programmer/Coach) always; athletes per the session's policy (`ADVANCE`,
  `DAY_OF`, `AFTER`, `AFTER_PARTICIPANTS`, `PARTICIPANTS_ONLY`; a program only `ADVANCE`/`DAY_OF`;
  default in Aldebaran `gym_session_settings`, gym time zone). Hidden content is null in responses,
  and session WODs and their leaderboards are 403. Admin: lists show only their own gyms (another
  gym on explicit `gymId`), any session by id. Write: `WOD_WRITE`; personal session (`gymId =
  null`): author only. "Séance faite" creates the athlete's journal (`session_logs`, participant);
  a journal locks the session (409), visibility stays editable. Competition block (physical box):
  the block's WOD is a box competition WOD (the Open is copied), athletes submit their score to the
  judges. `SessionResponse.capabilities {canModify, locked, contentVisible, canLog}`.
- Internal API: Antares `/antares/internal/**` (membership check, gym type; Aldebaran → `http://antares-auth:8080`,
  `X-Internal-Secret`, 404 without it, excluded from the Traefik `/antares` route). Aldebaran calls it
  through `client/AntaresClient` only.
- Gym settings: `MANAGE_SETTINGS`; deletion and ownership transfer: holder or admin (`@security.isGymHolder`);
  memberships: `MANAGE_MEMBERSHIPS` (Antares `@security`).
- Aldebaran returns `WodResponse.capabilities {canModify, locked, canJudge}` (computed per request, outside the
  WOD cache) so Sirius shows edit/delete only when allowed. Prefer this per-resource pattern for new
  features (e.g. scores) over replicating rules client-side.

Admin support session: a platform admin acts as a real (non-admin) user from Sirius `/admin/support`
(`POST /antares/admin/support-sessions {userId, reason}`). Antares sets a separate 30-min, non
refreshable `support_token` cookie (admin cookies untouched), records it in `support_sessions`, and
resolves requests as the target; writes are allowed and audited (`X-Auth-Actor-Id`). Expiry → 401
ProblemDetail `code=SUPPORT_SESSION_EXPIRED`, never a silent fallback to admin; password change,
account deletion and preference (language/theme/palette/unit system) updates → 403 `SUPPORT_SESSION_FORBIDDEN`
(athlete profile and body weights stay editable, audited);
Sirius keeps the admin's language/theme/palette/unit on screen. `/users/me` carries `supportSession` while active.

Account (lot H): Antares mails single-use links (password reset 30 min, e-mail verification 48 h;
hashed tokens in Redis, sent in the URL fragment) over generic SMTP (`MAIL_*` env vars, local Mailpit
`deneb-mail`, UI http://localhost:8025). An unverified address cannot join or request a gym (403
`EMAIL_NOT_VERIFIED`); registrations and reset requests are throttled per client IP (last
`X-Forwarded-For` entry, set by Traefik). A password reset ends every session (refresh token deleted,
older access tokens refused). Athlete profile (division, birth year, height, body weights): only the
division is shared; the rest is private to its owner, never on a leaderboard.

Temporary dev data (`dev` Spring profile, set in `docker-compose.yml`, to be removed like the CSV
export): test gyms "Spartacus CrossFit" (physical), "Zeus Programming" (virtual), "Pending Box" and
their accounts (Antares; `free@athlete.com` shows its name on public leaderboards), gym WODs and
scores for them (Aldebaran).

## Conventions

- Backend code, comments, Javadoc and commit messages in English. User-facing text is translated
  (FR + EN) on both sides: `messages*.properties` in backends, `sirius-app/src/assets/i18n/{fr,en}.json`.
- Schema changes go through new Flyway migrations (`src/main/resources/db/migration`, schemas
  `antares` / `aldebaran`, `ddl-auto=validate`); never edit an applied migration.
- Default branch `main`; dependabot opens most PRs. Branch before committing; commit only when asked.
- Frontend DTOs in `sirius-app/src/app/api/**/models` mirror backend DTOs: change both together.
- Catalog: a movement variant is the `equipment` / `techniques` of the movement and of the WOD movement
  (Aldebaran rejects values the movement does not allow), never a new movement or a note. Muscles have
  one functional group level (`parent`, same region). Never use a movement name absent from the catalog.
- WODs: `wodType` = format (For Time, Chipper, Ladder, AMRAP, EMOM, Death by...), optional `category` (Girls, Hero, Open...),
  structure in fields, not in a free scheme: `rounds` (Helen = For Time + 3 rounds), `restSeconds`,
  `workSeconds` (intervals, Tabata), `ladderStart`/`ladderStep` (ascending ladder, Death by), `repScheme`
  for a descending ladder or strength sets, weight vest `vestWeight(Female)`, buy-in / buy-out lines
  (`WodMovement.segment`);
  `scoreType` as officially scored (EMOM such as Chelsea = `ROUNDS`, 1RM = `WEIGHT`, every AMRAP =
  `REPS` stored as total reps, shown/entered as rounds + reps when the WOD has `repsPerRound`),
  optional official tiebreak text (`tiebreakEn/Fr`); a score's tiebreak is always optional.
  WOD texts are FR/EN pairs (`*En` / `*Fr`); men/women pairs for load (`weight`/`weightFemale`), calories and
  box/target height, stored in the prescribed unit and converted only for display (user preference
  `unitSystem` in Antares, Sirius `UnitService`). Put a measurable prescription in these fields, not in
  the notes.

## Gotchas

- Wording: CrossFit French keeps English jargon. Physical gym = "box" (FR/EN), virtual gym = "prog"
  (FR) / "program" (EN), roles Owner / Programmer / Coach / Athlète; code keeps `gym`.
- `.env` (secrets) is git-ignored: never print or commit it.
- Muscle/movement/public-WOD CSVs (`aldebaran-training/src/main/resources/*.csv`) are a temporary,
  git-ignored local backup: the catalog is edited in Sirius, exported with `POST /aldebaran/export/*`,
  and reloaded by Aldebaran's `DataSeeder` after a database reset. Not a product feature; never commit
  them.
