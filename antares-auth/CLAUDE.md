# Antares (auth API)

Spring Boot 4.1 / Java 25 service, base path `/antares`, schema `antares` (Flyway), Redis for refresh
tokens and rate limiting. Owns users, gyms, memberships and authentication (JWT access/refresh tokens in
HttpOnly cookies, XSRF cookie `XSRF-TOKEN`). `./mvnw verify` runs unit tests and `*IT`
(Testcontainers, Docker required).

## Responsibilities to keep in mind

- Forward-auth for Aldebaran: `AuthenticationController.verifyApi` (`GET /antares/auth/verify/api`)
  delegates to `service/GatewayIdentityService`, which builds the `X-Auth-*` headers (contract in the
  root `CLAUDE.md`). Any header change must be mirrored in `dynamic/routes.yml` and in Aldebaran's
  `SecurityConfig`.
- Effective permissions: `model/Membership.effectivePermissions()` is the single rule; inside Antares
  only an ACTIVE membership grants them (`activePermissions()`, used by
  `AntaresSecurityService.hasAccess` and `MembershipSummary.permissions`). `Membership.isUsable()`
  decides which memberships are sent to Aldebaran (`MembershipSummary.usable`).
- Membership management: `MembershipService.capabilities` is the single rule (sent as
  `MembershipResponse.capabilities`, enforced by `updateMembership` / `removeMembership`). Leaving or
  removing sets INACTIVE (never delete a member with history); a refused or withdrawn PENDING request
  is deleted; `joinGym` reactivates an INACTIVE row; BANNED blocks joining. Wrong enrollment codes go
  through `EnrollmentAttemptService` (per-user Redis sliding window, 429 `TooManyAttemptsException`
  with `Retry-After`); never reset it on success.
- Gym-scoped endpoint security: `@PreAuthorize("@security.hasGymPermission(#gymId, 'MANAGE_SETTINGS')")`
  / `canManageMembership(...)` in `service/AntaresSecurityService` (platform ADMIN always passes).
- Support sessions (admin acts as a real user): `service/SupportSessionService` + cookie
  `support_token`, resolved in `config/JwtAuthenticationFilter` into a
  `SupportSessionAuthentication` (principal = target, `getActor()` = admin). Never fall back to the
  admin identity: an unusable session is a 401 `SUPPORT_SESSION_EXPIRED`. Sensitive operations are
  listed in `JwtAuthenticationFilter.FORBIDDEN_DURING_SUPPORT` (403 `SUPPORT_SESSION_FORBIDDEN`).
  Every endpoint returning the current user goes through `UserController.withSupportSession`
  (Sirius replaces its user with the response).
- Internal API `/antares/internal/**` (`controller/InternalController`): service-to-service only,
  separate `SecurityFilterChain` (`@Order(1)`) with `config/InternalSecretFilter` (shared
  `X-Internal-Secret`, constant-time, 404 otherwise), excluded from the Traefik route. Never expose
  user data there beyond what a caller needs to enforce its own rule.
- Gym holder: exactly one OWNER (physical) / PROGRAMMER (virtual) per gym (`Gym.holderRole`,
  `Gym.allowsRole`, unique index V4); changed only by `MembershipService.transferOwnership`; transfer
  and gym deletion are `@security.isGymHolder`. Gym creation is open (`PENDING_APPROVAL`, one waiting
  request per user, no creation token); discovery `GET /gyms` returns `GymPublicResponse` only.
- Bootstrap: the admin from `ApplicationConfig.adminUserInitializer`. `config/DevDataSeeder`
  (`dev` profile, temporary, TO BE REMOVED) seeds test accounts; Aldebaran relies on its names.

## Code conventions

- Same layering and style as Aldebaran: thin controllers with OpenAPI annotations (`@Operation`,
  `@ApiResponses`), services with business rules, record DTOs with `@Schema`, MapStruct mappers,
  Lombok, Javadoc on public members, Google Java style (2 spaces).
- Errors as localized `ProblemDetail` via `exception/GlobalExceptionHandler`; message keys in
  `messages.properties` + `messages_fr.properties`.
- No valid access token on a protected endpoint → 401 ProblemDetail from
  `config/ProblemDetailAuthenticationEntryPoint` (Sirius refreshes only on 401); authenticated but
  forbidden → 403. Never fall back to Spring's default 403 entry point.
- Tests: `*Test` unit tests (Mockito); `*IT` extend `config/BaseIntegrationTest`.
