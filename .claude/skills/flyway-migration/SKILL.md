---
name: flyway-migration
description: Add or change a database schema in StellarApex with a new Flyway migration (next version number, schemas antares / aldebaran, ddl-auto=validate). Use whenever an entity or table changes.
paths:
  - "**/src/main/resources/db/migration/**"
  - "**/model/entities/**"
  - "antares-auth/src/main/java/**/model/**"
---

# Flyway migrations

- Schema changes ONLY through a new migration in `<service>/src/main/resources/db/migration`
  (`antares-auth` -> schema `antares`, `aldebaran-training` -> schema `aldebaran`).
- Never edit or rename a migration that was already applied (merged or run locally): add a new one.
- Name: `V<next>__Short_description.sql`, where `<next>` is the highest existing number in THAT
  service + 1 (`ls <service>/src/main/resources/db/migration | sort -V | tail -3`). Each service
  numbers on its own. If another open branch took the number, renumber yours before merging.
- Hibernate runs with `ddl-auto=validate`: entity and migration must match exactly (types, nullability,
  constraints, indexes), otherwise the context fails at start.
- Existing rows: add a default or a backfill so the migration works on a populated database.
- Keep the entity, the DTOs (and Sirius models in `sirius-app/src/app/api/**/models`) and the README
  in sync with the schema change.
- Verify with `./mvnw verify` in the service (ITs apply every migration on a fresh PostgreSQL).
