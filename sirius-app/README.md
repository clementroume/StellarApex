# Sirius (Frontend)

Angular frontend of the StellarApex platform: movement and muscle catalog, WODs (list, detail, form),
account pages and the admin support page. Available in French and English.

## Tech Stack

* **Framework**: Angular 22 (standalone components, signals, OnPush)
* **UI**: Tailwind CSS 4, DaisyUI 5, ng-icons (Heroicons, Huge Icons)
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

`npm start` proxies `/antares` and `/aldebaran` to `https://stellar.apex` (`proxy.conf.json`) and
rewrites the session cookies for `localhost`.

## Project Structure

```
src/app/
├── api/
│   ├── antares/      # models + services for auth, users, gyms, memberships
│   └── aldebaran/    # models + services for muscles, movements, WODs, scores
├── core/             # guards, interceptors, layout, cross-cutting services
├── features/         # one folder per feature (list / detail / form + routes), lazy-loaded
└── shared/component/ # reusable list, detail and search components
```

## Rights in the UI

The backends enforce every rule; Sirius only adapts what it shows:

* `core/services/access.service.ts` answers every rights question (admin, memberships, gym
  permissions) from `GET /antares/users/me`.
* Resource-level flags come from the API, e.g. `WodResponse.capabilities` (`canModify`, `locked`).
* **My gyms** (profile page): each membership with role, status and effective permissions, each
  permission explained in one sentence.
* **WOD list**: type filters come from `GET /aldebaran/wods/facets` (only types that return
  results); the create button follows the tab (gym WOD where the user programs, personal
  otherwise); on the "All" tab an admin can pick any gym or any athlete with personal WODs.
* **Admin support session** (`/admin/support`, admins only): search a user (2 characters minimum),
  filter by gym, role and membership status, page through results, or pick one of the 5 accounts
  recently acted as; give a reason and act as them for 30 minutes
  (`core/services/support-session.service.ts`). A banner shows the target and
  the remaining time; password change and account deletion are hidden; language and theme stay those of the admin's
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
  "My gym(s)" box, in the user's gyms only (names from Antares, others anonymous); the score form only
  asks the gym of a public WOD score to members of several gyms, and "My scores on this WOD" lists the user's scores (level, PR badge, notes) with
  edit/delete when the backend allows it, and opens the log/edit form: fields follow the score type
  (time or reps at the time cap, rounds on the minute for an EMOM, load in the user's unit, calories,
  distance). AMRAP scores are total reps; when the WOD has reps per round they are shown as
  "312 reps · 10 rounds + 12 reps" and can be typed either way (live conversion). The WOD form
  suggests the reps per round from the movements. WODs with an official tiebreak (Open) show it and offer an optional,
  collapsed tiebreak time. The WOD form proposes the usual score type of the chosen format.
* **Competitions**: the WOD form has a "Competition" toggle for gym WODs (a competition WOD is never
  public), and an original WOD offers "Copy to my gym" in its title bar to users who program a gym (copy of an
  Open WOD checked as competition by default). A copy shows its gym next to its title and a link to
  its original on the right, and cannot be copied again. On a competition WOD the judge
  (`capabilities.canJudge`) picks the athlete when logging a score (gym members from Antares); the
  leaderboard shows every score to judges (to verify them) and verified scores only to the others by
  default, with a "Verified" badge and Verify / Withdraw / edit / delete buttons when the backend
  allows them; the history shows "Verified by" / "Logged by".
* **Units**: a "Units" tab in My account (metric or imperial, `core/services/unit.service.ts`), saved
  in the account (`unitSystem`); without a choice it follows the language (French: metric). Values are
  converted for display only, rounded as gyms write them: loads (95 lb = 43 kg), heights (24/20 in =
  60/50 cm, 10/9 ft = 3/2.7 m) and distances (metric converts every imperial distance; imperial keeps
  runs and rows in m/km and converts only distances under 100 m to feet).

* **WOD form**: the movement is picked in a search box (name, abbreviation shown in the suggestions).
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
