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

* **WOD detail**: written like the gym whiteboard: "Fran (Girls / For Time)" with the time cap as a
  badge ("Tc : 10'"), the rep scheme, then one line per movement: quantity (omitted when it repeats
  the rep scheme), name, variants and "@men/women load" in the user's unit. Texts show in the user's
  language, falling back on the other one. The list shows "category / format" and filters on both.
* **Load unit**: kg/lb toggle in the navbar and a choice in Settings (`core/services/unit.service.ts`),
  saved in the account (`unitSystem`); without a choice it follows the language (French: kg). Loads
  are converted for display only (95 lb = 43 kg).

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
