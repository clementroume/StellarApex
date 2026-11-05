---
name: i18n-pair
description: Keep user-facing texts translated in French AND English, with the CrossFit wording of StellarApex. Use when adding or changing any visible text, message key or error message.
paths:
  - "sirius-app/src/assets/i18n/*.json"
  - "sirius-app/src/**/*.html"
  - "**/messages*.properties"
---

# FR + EN for every visible text

- Sirius: every key goes into BOTH `sirius-app/src/assets/i18n/fr.json` and `en.json`, same key, same
  place in the file. Nothing hard-coded in templates.
- Backends (Antares, Aldebaran): every key goes into BOTH `messages.properties` (EN) and
  `messages_fr.properties` (FR) of the service; bean validation keys use `{validation.*}`.
- Before finishing, compare the key sets of both files of each pair; no key may exist in one only.
- Backend code, comments and commits stay in English; only displayed text is translated.

## Wording

CrossFit French keeps the English jargon (WOD, AMRAP, EMOM, For Time, PR, Rx, scaled...).

| Concept | FR | EN |
|---|---|---|
| Physical gym | box | box |
| Virtual gym | prog | program |
| Roles | Owner, Programmer, Coach, Athlète | Owner, Programmer, Coach, Athlete |

Never write "salle" (code keeps `gym`). Keep user-facing texts short and consistent with existing keys:
search the files for a similar sentence before inventing a new one.
