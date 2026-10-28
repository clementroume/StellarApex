# Antares Auth API

This is the authentication backend service for the StellarApex platform, responsible for user
authentication, session management, user profiles, gyms and memberships. It is also the Forward Auth
server Traefik calls before every Aldebaran request. It is a secure, stateless Spring
Boot application designed to run as a containerized microservice.

## Tech Stack

* **Framework**: Spring Boot 4.1
* **Language**: Java 25
* **Security**: Spring Security 7 (stateless), OAuth2 Resource Server (JWT)
* **Authentication**: HttpOnly Cookies (Access + Refresh Tokens)
* **CSRF Protection**: Double-Submit Cookie
* **Database**: PostgreSQL (managed by Flyway migrations)
* **Cache**: Redis (for secure refresh token storage)
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

*This command runs all unit tests (`*Test.java`) and integration tests (`*IT.java`). Integration
tests start PostgreSQL and Redis with Testcontainers: Docker must be running.*

To build the JAR *without* running tests:

```bash
./mvnw clean package -DskipTests
```

### Build the Docker Image

This project uses a multi-stage `Dockerfile` to create a minimal, non-root, distroless JRE image.

The image is built as part of the root `docker-compose.yml`.

```bash
# From the project root
docker compose build antares-auth
```

## How to Run

This service is designed to be run as part of the full StellarApex stack using Docker Compose. This
ensures all dependencies (Database, Redis, Traefik) are correctly networked and configured.

### Run with Full Stack (via Root Docker Compose)

To run the entire platform (Traefik, Frontend, API, Admin, DB, and Cache), use the
`docker-compose.yml` in the project root.

1. **Build and start the services:**
   ```bash
   docker compose up --build -d
   ```

2. **Access the API:** The API will be available through the Traefik proxy at:

- https://stellar.apex/antares (Public API)
- https://docs.stellar.apex (Swagger UI through Bellatrix - Requires Admin Access). The raw OpenAPI
  document is served at `/antares-docs`.

3. **Logs:** To follow the logs for this specific service:

```Bash
docker compose logs -f antares-auth
```

## API Endpoints

### Authentication (`/antares/auth`)

* `POST /register`: Register a new user.
* `POST /login`: Authenticate and receive HttpOnly session cookies.
* `POST /logout`: Invalidate session, end any active support session and clear cookies.
* `POST /refresh-token`: Use the refresh token (cookie) to get a new access token.
* `GET /verify/admin`: Forward Auth for admin-only services (e.g., Grafana, Traefik).
* `GET /verify/api`: Forward Auth for backend API services (e.g., Aldebaran). Sends
  `X-Auth-User-Role` (platform role) and `X-Auth-Memberships` (`gymId:ROLE:PERM+PERM,...`, usable
  memberships with effective permissions). During a support session these describe the target
  user, plus `X-Auth-Actor-Id` (the admin). See `GatewayIdentityService`.

### User (`/antares/users`)

* `GET /me`: Get the profile of the currently authenticated user.
* `PUT /me/profile`: Update the user's first name, last name, or email.
* `PATCH /me/preferences`: Update the user's locale (language), theme and unit system (`unitSystem`:
  `METRIC`, `IMPERIAL`, or null to follow the language). The preferences are replaced as a whole,
  except `publicLeaderboardName` (show the name on public leaderboards, off by default) and `palette`
  (UI colours: `ocean` by default, `chalk`, `volt`, `rig`), kept when omitted. Refused during a support session.
* `POST /display-names` (`{userIds}`, at most 100): "First L." names the caller may see: themselves,
  members of their gyms (usable memberships), users who opted in to public leaderboards; all for
  platform admins. Hidden users are left out (clients show them as anonymous).
  Refused during a support session.
* `PUT /me/password`: Change the user's password (refused during a support session).
* `DELETE /me`: Delete the current user's account (refused during a support session).

During a support session `GET /me` returns the target user with
`supportSession {id, actor {id, firstName, lastName}, reason, expiresAt}` (null otherwise).

### Administration (`/antares/admin`, platform ADMIN)

* `GET /users?search=&gymId=&role=&status=&page=&size=`: Paginated `Slice` (20 per page, max 50) of
  users matching first name, last name or email (case-insensitive), ordered by last name, with
  their memberships. The optional `gymId`, `role` and `status` must hold on the same membership.
* `GET /users/lookup?ids=`: User summaries by ids (e.g. WOD authors in the admin WOD filters).
* `GET /support-sessions/recent`: The last 5 users the admin acted as, latest first.
* `POST /support-sessions` `{userId, reason}`: Start a support session (see below). 201
  `{id, actor, target, reason, startedAt, expiresAt}`; 403 admin target, 404 unknown user, 409
  already active.
* `DELETE /support-sessions/current`: End it and clear its cookie. 204, idempotent.

### Gym Management (`/antares/gyms`)

* `POST /` `{name, description, isProgramming}`: Request a gym. Any user; the gym is
  `PENDING_APPROVAL` and the requester becomes its OWNER (box) or PROGRAMMER (program). 409 if the
  name is taken (case-insensitive) or the user already has a request waiting for approval.
* `GET /?search=&type=BOX|PROGRAM&page=&size=`: `Slice` of ACTIVE gyms by name (20 per page, max 50),
  public view `{id, name, description, isProgramming}` only.
* `GET /{id}`: Public view of an ACTIVE gym (e.g. invitation link `/join?gym=12&code=ABC`), else 404.
* `PUT /{gymId}` `{name, description}`: [`MANAGE_SETTINGS`] Rename or describe the gym.
* `POST /join` `{gymId, enrollmentCode}`: Join an ACTIVE gym (code compared ignoring case); returns the
  user's `MembershipSummary`, PENDING or ACTIVE (auto-subscription). A former (INACTIVE) member is
  reactivated as ATHLETE on the same membership. 400 wrong code (a malformed request, not a missing
  right), 404 unknown or inactive gym, 409 already a member or BANNED. Wrong codes are throttled per
  user (`EnrollmentAttemptService`, Redis sliding window): after 5 wrong codes within 15 minutes
  (`application.security.enrollment.*`), every join is refused with 429, a `Retry-After` header and
  `retryAfter` (seconds) until the oldest one leaves the window; a successful join does not reset it.
* `DELETE /{gymId}/membership/me`: Leave: ACTIVE becomes INACTIVE (history and scores kept), a PENDING
  request is withdrawn (deleted). 409 for the OWNER/PROGRAMMER (transfer first) and for a banned user.
* `PUT /{id}/status?status=`: [ADMIN] Approve (`ACTIVE`), reject (`REJECTED`) or suspend (`SUSPENDED`).
* `GET /{gymId}/settings`: [`MANAGE_SETTINGS`] Name, description, type, status, enrollment code and
  auto-subscription.
* `PUT /{gymId}/settings` `{enrollmentCode, isAutoSubscription}`: [`MANAGE_SETTINGS`] Update them.
* `POST /{gymId}/settings/enrollment-code`: [`MANAGE_SETTINGS`] New random code (former invitation
  links stop working).
* `GET /{gymId}/athletes`: [`SCORE_VERIFY`] Usable members of the gym (`{id, displayName}`, sorted by
  name), for the staff logging competition scores.
* `POST /{gymId}/transfer-ownership` `{membershipId}`: [gym OWNER/PROGRAMMER] Make an ACTIVE member
  the gym holder; the former holder becomes COACH (physical) or ATHLETE (virtual). Returns both
  memberships.
* `DELETE /{id}`: [gym OWNER/PROGRAMMER] Delete the gym and its memberships (also withdraws a
  rejected request); a coach holding `MANAGE_SETTINGS` cannot.

### Membership Management (`/antares/memberships`)

* `GET /?gymId=&status=`: [`MANAGE_MEMBERSHIPS`] Members of a gym, PENDING first then ACTIVE, INACTIVE,
  BANNED, by name, each with `createdAt` and the caller's `capabilities {canChangeStatus,
  canChangeRole, canChangePermissions, canRemove, canBan, canBecomeHolder}`
  (`MembershipService.capabilities`, the single rule also enforced by the two endpoints below).
* `PUT /{id}` `{status, gymRole, permissions}`: [`MANAGE_MEMBERSHIPS`] Update a member. The
  OWNER/PROGRAMMER and platform admins manage everyone else (roles and permissions on a physical box
  only, bans); a COACH with `MANAGE_MEMBERSHIPS` changes the status of ATHLETES only, never to or from
  BANNED; the holder's own membership never changes here.
* `DELETE /{id}`: [`MANAGE_MEMBERSHIPS`] Refuse a PENDING request (deleted, the user may ask again) or
  remove a member (INACTIVE, history kept). 409 for the OWNER/PROGRAMMER.

A BANNED member keeps the membership row, which blocks any new request until the ban is lifted
(status INACTIVE).

Bracketed permissions are gym permissions; a platform ADMIN always passes.

### Internal API (`/antares/internal`)

Called by Aldebaran on the Docker network (`http://antares-auth:8080`), never through Traefik (the
public `/antares` route excludes `/antares/internal`). Every request needs `X-Internal-Secret`
(`INTERNAL_SECRET`, checked in constant time by `config/InternalSecretFilter`, own filter chain);
without it the answer is a bare 404.

* `GET /gyms/{gymId}`: `{programming}`, whether the gym is a virtual programming (404 if unknown).
* `GET /gyms/{gymId}/members/{userId}`: `{member}`, whether the user holds a usable membership in the
  gym (ACTIVE, and ACTIVE gym or OWNER/PROGRAMMER).

## Roles and Permissions

* **Platform role** (`USER`, `ADMIN`) on the user.
* **Gym role** (`OWNER`, `PROGRAMMER`, `COACH`, `ATHLETE`) and granted permissions on each membership.
* **Effective permissions** (`Membership.effectivePermissions()`, the single rule used for endpoint
  security, Forward Auth headers and `MembershipSummary.permissions` in `GET /users/me`):
  OWNER and PROGRAMMER hold every permission, a COACH holds the granted ones, an ATHLETE holds none.
* Roles by gym type: a physical gym has exactly one OWNER plus coaches and athletes; a virtual
  (programming) gym has exactly one PROGRAMMER plus athletes. `PUT /memberships/{id}` never assigns
  or removes OWNER/PROGRAMMER (400); only `transfer-ownership` does. A partial unique index (V4)
  enforces one holder per gym.
* A membership is sent to Aldebaran only when it is usable: status ACTIVE and gym ACTIVE (or role
  OWNER/PROGRAMMER while the gym is pending approval); a suspended or rejected gym opens access to
  nobody. `MembershipSummary.usable` tells Sirius which ones these are.
* Inside Antares a membership grants its effective permissions only while ACTIVE
  (`Membership.activePermissions()`): an INACTIVE coach manages nothing.

## Support Sessions

An admin can act as a real (non-admin) user, e.g. to reproduce a bug a gym reports:

* The session lasts 30 minutes and is never refreshed. Its signed JWT (`sub` target, `act` admin,
  `sid` session, `typ=support`) is in the HttpOnly, SameSite=Strict cookie `support_token`; the
  admin's own cookies are kept.
* It is honoured only with a valid access token of that same admin. Expired, ended or mismatched
  sessions get 401 ProblemDetail `code = SUPPORT_SESSION_EXPIRED` and the cookie is cleared: never a
  silent fallback to admin rights.
* `PUT /users/me/password`, `DELETE /users/me` and starting another session get 403
  `code = SUPPORT_SESSION_FORBIDDEN`.
* `/antares/auth/**` (except `/verify/api`) and `DELETE /admin/support-sessions/current` ignore the
  cookie.
* Every session is stored in `support_sessions` and logged at INFO on start and end.

## Bootstrap Data

At startup, the platform admin defined by the `ADMIN_*` environment variables is created if no
admin exists (`ApplicationConfig`).

**Temporary (to be removed):** with the `dev` profile (`SPRING_PROFILES_ACTIVE: dev` in the root
`docker-compose.yml`), `DevDataSeeder` creates test gyms (Spartacus CrossFit, Zeus Programming,
Pending Box) and accounts (`owner@sparta.com`, `coach@sparta.com`, `programmer@zeus.com`, ... see
its Javadoc), all with the admin's default password; `free@athlete.com` shows its name on public
leaderboards. Aldebaran's dev seeders (WODs, scores) rely on these names.

## Database Migrations

Flyway manages database schema changes (schema `antares`, `ddl-auto=validate`). SQL migration scripts are located in
`src/main/resources/db/migration`.

Flyway runs automatically on application startup to apply any pending migrations.