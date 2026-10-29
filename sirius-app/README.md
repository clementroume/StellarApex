# Sirius (Frontend)

Angular frontend of the StellarApex platform: navigation by spaces (Today, My training, My box,
Catalogue, Admin), daily sessions (week planning, staff session form, "Séance faite", journal),
movement and muscle catalog, WODs (list, detail, form), scores, account pages,
box/prog memberships and management, and the admin pages (boxes & programs, support). Mobile first, installable (PWA), available in French and English.

## Tech Stack

* **Framework**: Angular 22 (standalone components, signals, OnPush)
* **UI**: Tailwind CSS 4, spartan/ui (`@spartan-ng/brain` primitives + helm styles copied in
  `src/app/shared/ui`, on Angular CDK), ng-icons (Heroicons, Huge Icons, Lucide for helm)
* **PWA**: Angular service worker (production build), `public/manifest.webmanifest`
* **i18n**: ngx-translate (`src/assets/i18n/fr.json`, `en.json`)
* **Tests**: Vitest (jsdom) through `ng test`
* **Serving**: nginx (multi-stage `Dockerfile`, `nginx.conf`)

## Prerequisites

* Node.js 26
* The StellarApex Docker stack running, for local development against the real APIs

## Commands

```bash
npm ci              # install dependencies
npm start           # dev server on http://localhost:4200
npm test            # unit tests (single run)
npm run build       # production build (dist/sirius-app)
```

`npm start` proxies `/antares` and `/aldebaran` to the local Traefik (`https://localhost` with
`Host: stellar.apex`, `proxy.conf.json`; the Docker stack must run) and rewrites the session cookies
for `localhost`, so the app on `http://localhost:4200` works with the dev seed accounts.

## Project Structure

```
src/app/
├── api/
│   ├── antares/      # models + services for auth, users, gyms, memberships
│   └── aldebaran/    # models + services for muscles, movements, WODs, scores
├── core/             # guards, interceptors, layout, cross-cutting services
├── features/         # one folder per feature (list / detail / form + routes), lazy-loaded
├── shared/component/ # app components: page template, form field, list group/item/state, detail, movement
│                     # combobox, box/prog selector, palette picker, native select, coming soon...
└── shared/ui/        # spartan helm code (imported as @spartan-ng/helm/<name>)
src/palettes.css      # colour palettes × light/dark (--sa-* variables)
src/spartan.css       # spartan tokens and Tailwind colours mapped to the palettes
```

## Navigation

* **Spaces** (`core/layout/spaces.service.ts`): Today (`/today`, home) · My training (`/training`:
  journal of the sessions done and of the scores, personal sessions, records and review to come) ·
  My box (`/box`, members only: sessions, leaderboards to come; members and settings for staff holding `MANAGE_MEMBERSHIPS` /
  `MANAGE_SETTINGS`) · Catalogue (`/wods`, `/movements`, `/muscles`) · Admin (`/admin`, admins).
  Desktop: spaces in the top bar, the space's pages as tabs below it. Mobile: bottom tab bar.
* **Displayed box/prog** (`core/services/display-gym.service.ts`): selector in the top bar when the
  user has several usable memberships; one box/prog or all, remembered on the device. A display
  filter only (Today, journal): never sent to the APIs, rights stay per resource.
* **Today**: today's sessions of the displayed box/prog and personal ones (`GET /aldebaran/sessions`
  for today: WOD of the day, specialties of the week) with "Séance faite", then the latest scores and
  PRs from `GET /aldebaran/scores/me`.
* **Sessions** (`features/sessions`): My box › Sessions is the week of the displayed box/prog (one
  row per day, specialties apart; frames with block themes; Owner/Programmer/Coach with `WOD_WRITE`
  get "New session"). The session page shows the frame, then the content when its visibility policy
  opens it (`SessionResponse.capabilities`): "Séance faite" (journal, every block ticked, untick what
  was skipped), per-block scores (score form with sets and "Submit for verification" on a competition
  block), the judge's queue of submitted scores, and for staff edit / delete (until someone logged it)
  and the visibility dialog. The session form (staff and personal) stays light: type and day,
  visibility on one line, title and coach's word folded; Skill and WOD blocks from the start (not
  removable in a WOD session), warm-up always first, cool down always last (text only), accessory /
  finisher optional; blocks are lettered A, B, C from their order and carry a theme shown in advance.
  Each block's WOD is written with the quick WOD editor or borrowed (a standalone WOD; "Program in a
  session" on a WOD page prefills it, as a competition block for an official one). Session settings
  (default policy, time zone) sit in My box › Settings.
* **Quick WOD editor** (`features/wods/wod-quick-form`, session blocks and My training › My WODs):
  format chips (For Time, Rounds, Chipper, Ladder, AMRAP, EMOM / intervals, Tabata, Death by,
  Strength, Skill) with the usual score type (changeable) and only the structure fields of the
  format (rounds and rest, descending values or ascending start + step, every X minutes with optional
  work / rest, Tabata 8 × 20 s / 10 s, Death by start + step, strength sets `5x5` / `5-5-3-3-1`); no
  free rep scheme. Each movement line has a measure (reps, max, distance, calories, time, picked from
  the movement: run → distance, rower → calories, plank → time), the load / height its category needs
  (other measures, equipment and technique folded) and, on Rounds and AMRAP, a buy-in / buy-out part. Weight vest
  and notes (writer's language) are folded options; title generated when empty. The full WOD form stays for admins writing public WODs.
* **PWA**: installable (manifest, icons, service worker in production builds); API calls are never
  cached; a toast offers to reload when a new version is ready.

## Rights in the UI

The backends enforce every rule; Sirius only adapts what it shows:

* `core/services/access.service.ts` answers every rights question (admin, memberships, gym
  permissions) from `GET /antares/users/me`.
* Resource-level flags come from the API, e.g. `WodResponse.capabilities` (`canModify`, `locked`).
* **My boxes & programs** (`/my-account/gyms`): memberships with role, status and permissions (each
  explained in one sentence), former memberships, leave / withdraw a request; join a box or program
  (`gyms/join`: search by name and type, enrollment code; the invitation link `/join?gym=&code=`
  pre-fills both, after sign-in or sign-up if needed); request a new one (`gyms/new`, admin approval).
* **Account e-mails** (`features/auth`): "forgot password" (`/auth/forgot-password`, same answer
  for any address), reset (`/auth/reset-password#token=...`, then sign in again: every session ended)
  and verification (`/auth/verify-email#token=...`, works signed out). Mailed links carry the token
  in the URL fragment, read and removed from the address bar by `auth-token.ts`. An unverified
  address (`UserResponse.emailVerified`) shows a badge and a resend button in My information and a
  notice on the join / request pages (`shared/component/email-verification-notice`).
* **Athlete profile** (`/my-account/athlete`): division (men/women), birth year, height, body weight
  history, typed and shown in the user's unit system (stored in cm / kg); only the division is
  shared. WOD detail: in a men/women pair, the value of the user's division is underlined and the
  other muted (`UnitService.loadPair` / `distancePair`). Leaderboards: All / Men / Women filter
  (all by default, as scores logged before a division was chosen have none).
* **Sign-in / sign-up `returnUrl`**: kept between the two pages and checked by
  `features/auth/return-url.ts` (app paths, or http(s) URLs on the current host and its subdomains for
  the admin tools behind Traefik); anything else goes to `/today` (no open redirect).
* **My box › Members / Settings** (staff with `MANAGE_MEMBERSHIPS` / `MANAGE_SETTINGS`): the managed
  box/prog follows the displayed one when allowed (picker when several). Members: pending requests
  first, status and role filters, validate / refuse / remove / ban / role and permissions as
  `MembershipResponse.capabilities` allow. Settings: name and description, enrollment code (copy,
  edit, regenerate), invitation link, auto-subscription; the owner/programmer also gets the danger
  zone (transfer to a member Antares marks `canBecomeHolder`, deletion after typing the name).
* **Admin › Boxes & programs** (`/admin/gyms`): approval queue by default, search, approve, reject,
  suspend, reactivate, with the requester and the active member count.
* **WOD catalogue** (`/wods`): public WODs only (Girls, Hero, CrossFit benchmarks, Open...), category
  and format filters from `GET /aldebaran/wods/facets`; admins create them. Personal WODs live in My
  training › My WODs (`/training/wods`, quick editor).
* **Admin support session** (`/admin/support`, admins only): search a user (2 characters minimum),
  filter by gym, role and membership status, or by account (no box or program, e-mail not
  verified, badge on each row), page through results, or pick one of the 5 accounts
  recently acted as; give a reason and act as them for 30 minutes
  (`core/services/support-session.service.ts`). A banner shows the target and
  the remaining time; password change and account deletion are hidden; language, theme and palette stay those of the admin's
  screen and are never saved to the user's account; when the session expires the
  app reloads the admin identity instead of refreshing tokens.

## Catalog Screens

* **WOD detail**: the title, then two boxes that never repeat an information. Left: visibility,
  category, modalities and locked badges, score type, "My best score" (the PR with its level and rank, or a
  "Log my score" button), description and notes. Right, the gym whiteboard: the format with its duration ("AMRAP 20 minutes",
  "EMOM 10 minutes", or "For Time" with the time cap as a badge), the rep scheme, then one line per
  movement: quantity (reps omitted when they repeat the rep scheme, "14/12 cal", distance), name,
  variants and the men/women load and height ("61/43 kg", "60/50 cm"). Texts show in the user's
  language, falling back on the other one. The list shows "category / format" and filters on both.
  Below the boxes, "Leaderboard" ranks each athlete's PR by level, worldwide or, with the
  "My box(es)" / "My program(s)" boxes, in the user's boxes and/or programs only (names from Antares, others anonymous); the score form only
  asks the gym of a public WOD score to members of several gyms, and "My scores on this WOD" lists the user's scores (level, PR badge, notes) with
  edit/delete when the backend allows it, and opens the log/edit form: fields follow the score type
  (time or reps at the time cap, rounds on the minute for an EMOM, load in the user's unit, calories,
  distance). AMRAP scores are total reps; when the WOD has reps per round they are shown as
  "312 reps · 10 rounds + 12 reps" and can be typed either way (live conversion). The WOD form
  suggests the reps per round from the movements. WODs with an official tiebreak (Open) show it and offer an optional,
  collapsed tiebreak time. The WOD form proposes the usual score type of the chosen format.
* **Competitions**: the WOD form has a "Competition" toggle for physical gym WODs (never for a virtual
  programming, `MembershipSummary.gymProgramming`; a competition WOD is never public); the copy
  targets are physical gyms only. A WOD page offers "Program in a session" to users who program a box or
  a program (an Open WOD becomes a competition block of the box, copied by Aldebaran). A copy shows its
  gym next to its title and a link to its original on the right. On a competition WOD the judge
  (`capabilities.canJudge`) picks the athlete when logging a score (gym members from Antares); the
  leaderboard shows every score to judges (to verify them) and verified scores only to the others by
  default, with a "Verified" badge and Verify / Withdraw / edit / delete buttons when the backend
  allows them; the history shows "Verified by" / "Logged by".
* **Palettes**: My account → Settings → Appearance offers four colour palettes (Ocean by default,
  Chalk and iron, Volt, Rig red), each in light and dark, saved in the account (`palette`). Defined in
  `src/palettes.css` (`data-palette` + `data-theme` on `<html>`, `core/services/theme.service.ts`).
* **Units**: a "Units" tab in My account (metric or imperial, `core/services/unit.service.ts`), saved
  in the account (`unitSystem`); without a choice it follows the language (French: metric). Values are
  converted for display only, rounded as gyms write them: loads (95 lb = 43 kg), heights (24/20 in =
  60/50 cm, 10/9 ft = 3/2.7 m) and distances (metric converts every imperial distance; imperial keeps
  runs and rows in m/km and converts only distances under 100 m to feet).

* **WOD form**: the movement is picked in a combobox (spartan/ui, name or abbreviation); each movement
  can prescribe a number of sets (shown as "5 x 5" on the whiteboard).
  The movement's allowed equipment and techniques then appear as toggle chips (loaded from
  `GET /aldebaran/movements/{id}`); changing the movement clears them. Aldebaran rejects any other
  value.
* **Muscles**: the list groups muscles by region, each functional group followed by its muscles; the
  form picks the group among the top-level muscles of the same region; a movement detail explains a
  group (its muscles) or a member (its group) in a tooltip. Coaching cues keep their line breaks.

## Docker

The image is built as part of the root `docker-compose.yml`:

```bash
# From the project root
docker compose build sirius-app
```
