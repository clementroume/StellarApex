# Sirius (Angular frontend)

Angular 22 standalone app, zoneless-friendly signals, Tailwind 4 + DaisyUI 5, ngx-translate, ng-icons,
Vitest (jsdom) through `ng test`. Commands: `npm test`, `npm run build`, `npm start` (see root `CLAUDE.md`).

## Layout

- `src/app/api/<service>/{models,services}`: one Angular service per backend resource, DTO interfaces
  mirroring the backend (`antares` = auth/users/gyms/memberships, `aldebaran` = muscles/movements/WODs/scores).
  URLs built from `environment.authUrl` (`/antares`) / `environment.trainingUrl` (`/aldebaran`).
- `src/app/core`: guards, interceptors, layout (navbar, main-layout, support banner), cross-cutting
  services, shared models (`Slice<T>`, `ProblemDetail`).
- `src/app/features/<feature>`: `<x>-list`, `<x>-detail`, `<x>-form` + `<feature>.routes.ts`, lazy-loaded
  from `app.routes.ts` under the authenticated `MainLayoutComponent`.
- `src/app/shared/component`: reusable `list-*`, `detail-*`, `search-input` components (inline templates).

## Rights in the UI

- Use `core/services/access.service.ts` for every rights question: `isAdmin()`, `memberships()`,
  `hasGymPermission(gymId, p)`, `gymsWithPermission(p)`. Never test `currentUser().platformRole`
  directly in components.
- For a specific resource, rely on backend-computed flags (e.g. `WodResponse.capabilities`) instead of
  recomputing rules. Guard: `adminGuard`.
- Admin support session: `core/services/support-session.service.ts` (start/end/expiry) and
  `core/layout/support-banner`. During a session `currentUser` IS the target (with `supportSession`),
  so rights follow automatically. `auth.interceptor` turns 401 `SUPPORT_SESSION_EXPIRED` into
  `handleExpired()` and never refreshes it. Any other API 401 triggers one refresh + retry; a failed
  refresh logs out only a known user (anonymous load: `authGuard` redirects).

## Code conventions (match existing files)

- Standalone components, `ChangeDetectionStrategy.OnPush`, `inject()` for DI, `input()` /
  `input.required()` and `output()`; route params arrive as inputs (`withComponentInputBinding`).
- Signals for state, `computed` for derived state; reactive forms with `fb.nonNullable.group`.
- Class body sections in this order with these comments: `// --- INJECTIONS ---`,
  `// --- STATE (SIGNALS & VARIABLES) ---`, `// --- COMPUTED STATE ---`, `// --- LIFECYCLE ---`,
  `// --- DATA FETCHING ---`, `// --- ACTIONS & EVENTS ---`, `// --- HELPERS ---`.
- Templates in a separate `.html` (except small shared components), new control flow (`@if`, `@for`),
  DaisyUI classes (`btn`, `card`, `badge`, `select select-bordered`...). Escape a literal `@` as `&#64;`.
- Every visible string goes through `| translate`; add keys to BOTH `src/assets/i18n/fr.json` and
  `en.json` (2-space JSON, keys grouped by feature: `GLOBAL`, `NAVBAR`, `MOVEMENT`, `WOD`, `ADMIN`...).
  Gym roles, statuses and permissions (label + description) are shared under `GYM.*`.
- Icons must be registered in `APP_ICONS` (`app.config.ts`) from `@ng-icons/heroicons/outline` or
  `@ng-icons/huge-icons`; check the name exists in `node_modules/@ng-icons/heroicons/types`.
- Lists: `BYPASS_LOADER` HttpContext to skip the global loader; UI state worth keeping across
  navigation lives in service signals (`savedSearchQuery`...); services expose `refreshNeeded$` +
  `notifyRefresh()` so forms/details can refresh lists.
- Errors: show `ProblemDetail.detail` from the backend when present (already localized), else a
  translated fallback; 409 = conflict/locked, 403 = forbidden.
- Wrap every `localStorage` / `sessionStorage` access in try/catch.
- Loads, distances and heights are shown through `core/services/unit.service.ts` (`formatLoad`,
  `formatDistance`: user's metric/imperial choice, men/women pairs "43/29 kg"); never print a stored unit.
  Lifted loads in scores use `formatScoreLoad` / `convertScoreLoad` (0.5 kg / 1 lb precision).
- Scores on the WOD detail: `features/wods/scores` (`WodScoresStore` provided by the detail, shared by
  `score-best`, `score-history`, `score-form`; `formatScore` in `score.utils.ts`).

## Tests

- One `*.spec.ts` next to each file. Component specs: `TestBed` with `provideRouter([])`,
  `provideIcons(APP_ICONS)`, `provideTranslateService()` (no translations loaded, so templates render
  the raw keys, which tests can assert), dependencies mocked with `{provide: X, useValue: mock}` built
  from `vi.fn().mockName('X.method')` and `signal(...)` for reactive state.
- HTTP services and interceptors: `provideHttpClient(withXhr(), ...)` + `provideHttpClientTesting()`,
  `HttpTestingController.expectOne(url)` and `verify()` in `afterEach`.
- Debounced code: `vi.useFakeTimers()` + `vi.advanceTimersByTime`, restore in `afterEach`.

## Known state

- Production build warns that the initial bundle is slightly over the 500 kB budget (`angular.json`).
