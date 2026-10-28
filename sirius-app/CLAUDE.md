# Sirius (Angular frontend)

Angular 22 standalone app, zoneless-friendly signals, Tailwind 4 + spartan/ui, ngx-translate, ng-icons,
Vitest (jsdom) through `ng test`. Commands: `npm test`, `npm run build`, `npm start` (see root `CLAUDE.md`;
the dev proxy targets the local Traefik `https://localhost` with `Host: stellar.apex`, Docker stack required).

## Layout

- `src/app/api/<service>/{models,services}`: one Angular service per backend resource, DTO interfaces
  mirroring the backend (`antares` = auth/users/gyms/memberships, `aldebaran` = muscles/movements/WODs/scores).
  URLs built from `environment.authUrl` (`/antares`) / `environment.trainingUrl` (`/aldebaran`).
- `src/app/core`: guards, interceptors, layout (navbar, bottom-nav, space-nav, `spaces.service`, main-layout,
  support banner), cross-cutting
  services, shared models (`Slice<T>`, `ProblemDetail`).
- `src/app/features/<feature>`: `<x>-list`, `<x>-detail`, `<x>-form` + `<feature>.routes.ts`, lazy-loaded
  from `app.routes.ts` under the authenticated `MainLayoutComponent`.
- `src/app/shared/component`: reusable `list-*`, `detail-*`, `search-input`, `movement-combobox`,
  `gym-selector` components (inline templates).
- `src/app/shared/ui`: spartan/ui helm code (styles copied from the spartan CLI, ours to edit), imported as
  `@spartan-ng/helm/<name>` (`tsconfig.json` paths) on top of `@spartan-ng/brain` (pinned version).

## UI primitives (D42: spartan/ui)

- New interactive widgets (combobox, select, dialog, sheet/drawer, menu, tabs, toast, date picker...)
  use spartan brain + helm, wrapped in `shared/component`; never hand-write ARIA/keyboard handling.
- No DaisyUI any more. The spartan preset is NOT imported: `src/spartan.css` keeps its state variants
  and maps every Tailwind colour and spartan token to the palette variables (`--sa-*`). Brand colours
  keep the app meaning (`primary`, `secondary` = records/PR yellow, `accent` = brand orange, with
  `*-content` for text on them); in helm code, spartan's subtle `secondary`/`accent` are `muted`.
- App pieces: `app-page` (page template), `app-form-field` (label + input group + error),
  `appNativeSelect` (native select keeping `ngValue`), `app-list-group`, `app-section-divider`,
  `app-coming-soon` / `SpacePlaceholderComponent` (route data), `field-group` and `switch` CSS
  utilities. Toggle filters are buttons with `aria-pressed`; dialogs trap focus (CDK), close on Escape
  and give focus back.
- Add a component: `npm i -D @spartan-ng/cli@<brain version>`, `npx ng g @spartan-ng/cli:ui <name>`
  (reads `components.json`), then `npm uninstall @spartan-ng/cli` (it pulls Nx and audit warnings) and
  check the diff: the CLI rewrites `tsconfig.json` (restore its header comment),
  installs lucide icons (keep `@ng-icons/lucide` on the `@ng-icons/core` major) and leaves `.nx/` (ignored).

## Theme and palettes

- `ThemeService` sets `data-theme` (light/dark) and `data-palette` (`ocean` default, `chalk`, `volt`,
  `rig`; Antares preference `palette`) on `<html>`. Colours live only in `src/palettes.css` as
  `--sa-*` variables, read by `spartan.css`. Never hard-code a colour: use the theme utilities
  (`bg-card`, `bg-background`, `text-muted-foreground`, `border`, `text-primary`, `bg-secondary/15`...).

## Navigation

- Spaces come from `core/layout/spaces.service.ts` (AccessService only): add a space or a space page
  there, plus its lazy routes in `app.routes.ts` / `<feature>.routes.ts`. Staff pages also get
  `gymPermissionGuard(permission)` (UI comfort, the backend decides).
- The displayed box/prog (`DisplayGymService`) filters what a screen shows; never send it to an API.
- Pages use `app-page`; features to come use `SpacePlaceholderComponent` with route data.

## Rights in the UI

- Use `core/services/access.service.ts` for every rights question: `isAdmin()`, `memberships()`,
  `hasGymPermission(gymId, p)`, `gymsWithPermission(p)`. Never test `currentUser().platformRole`
  directly in components.
- For a specific resource, rely on backend-computed flags (e.g. `WodResponse.capabilities`,
  `MembershipResponse.capabilities`) instead of recomputing rules. Guard: `adminGuard`.
- `AccessService.memberships()` = memberships with `usable` (computed by Antares). After joining,
  leaving, creating, transferring or deleting a gym, call `UserService.refreshCurrentUser()`.
- Staff pages pick the managed gym with `features/box/staff-gym.ts` (`staffGymSelection(permission)`:
  follows `DisplayGymService` when allowed, `app-staff-gym-picker` when several).
- Redirects after sign-in/sign-up go through `features/auth/return-url.ts` (`resolveReturnUrl`): never
  navigate to a raw `returnUrl`.
- Sensitive actions ask through `shared/component/confirm-dialog` (`app-confirm-dialog`, alert dialog,
  extra fields projected, `confirmDisabled`). Backend errors: `problemDetailOf(err)`.
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
  spartan helm (`hlmBtn`, `hlmCard`, `hlmBadge`, `hlmInput`, `app-form-field`...). Escape a literal `@` as `&#64;`.
- Dates are shown with `toLocaleDateString` in the language shown (no Angular locale data registered).
- Every visible string goes through `| translate`; add keys to BOTH `src/assets/i18n/fr.json` and
  `en.json` (2-space JSON, keys grouped by feature: `GLOBAL`, `NAVBAR`, `MOVEMENT`, `WOD`, `ADMIN`...).
  Gym roles, statuses and permissions (label + description) are shared under `GYM.*`.
- Icons must be registered in `APP_ICONS` (`app.config.ts`) from `@ng-icons/heroicons/outline` or
  `@ng-icons/huge-icons`; check the name exists in `node_modules/@ng-icons/heroicons/types`.
- Lists: `BYPASS_LOADER` HttpContext to skip the global loader; UI state worth keeping across
  navigation lives in service signals (`savedSearchQuery`...); services expose `refreshNeeded$` +
  `notifyRefresh()` so forms/details can refresh lists.
- Toasts: `NotificationService.showSuccess/showError` (spartan sonner, top centre).
- Errors: show `ProblemDetail.detail` from the backend when present (already localized), else a
  translated fallback; 409 = conflict/locked, 403 = forbidden.
- Wrap every `localStorage` / `sessionStorage` access in try/catch.
- Account e-mail links (`/auth/reset-password`, `/auth/verify-email`) carry the token in the URL
  fragment: read it with `features/auth/auth-token.ts` (`readLinkToken`, also removes it from history).
  Unverified users (`UserResponse.emailVerified`): `app-email-verification-notice` on pages whose
  action Antares refuses with 403 `EMAIL_NOT_VERIFIED`.
- ng-icons styles its host in a cascade layer `ng-icon`, declared first in `styles.css` (`@layer
  ng-icon;` before Tailwind) so `text-*` utilities on an icon win; keep that line first.
- Loads, distances and heights are shown through `core/services/unit.service.ts` (`formatLoad`,
  `formatDistance`: user's metric/imperial choice, men/women pairs "43/29 kg"); never print a stored unit.
  Lifted loads in scores use `formatScoreLoad` / `convertScoreLoad` (0.5 kg / 1 lb precision).
  Men/women pairs with the user's division highlighted: `loadPair` / `distancePair` (`MeasurePair.mine`);
  body weights / heights of the athlete profile: `bodyWeightFromKg` / `heightFromCm` and back.
- Competition WODs: `WodResponse.capabilities.canJudge` decides the athlete selector of the score form
  (`GymService.getAthletes`); per-score buttons follow `ScoreCapabilities.canModify/canVerify`;
  `WodScoresStore.setVerified`. The copy dialog lives in the WOD detail (`WodService.copyWod`).
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

- Initial bundle budget (`angular.json`): warning at 600 kB (raised from 500 kB in lot 0): ~580 kB
  (137 kB transferred; 520 kB before lot 0). Tailwind generates the utilities of every helm file
  (~145 kB of CSS) and helm in the shell brings `tailwind-merge` (~37 kB). Keep heavy brain modules out of the shell
  (sonner is loaded on first toast and its toaster is `@defer (on idle)`).
  `@spartan-ng/helm/utils` must not re-export `provideSpartanHlm` (it drags `@angular/cdk/overlay` in).
  The shell (navbar, bottom nav, layout) uses no overlay-based helm (select, dropdown, dialog, tooltip):
  native controls or `@defer`, or the CDK overlay and brain modules land in the initial bundle (~+175 kB).
