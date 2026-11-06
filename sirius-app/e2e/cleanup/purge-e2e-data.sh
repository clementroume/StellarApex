#!/usr/bin/env bash
# Deletes the data created by the e2e journeys (see purge-e2e-data.sql). LOCAL Docker stack only:
# refuses to run unless the running container is the local `castor-db`. Dry run by default; `--apply` commits.
# The journeys use the fixture accounts of Antares' `e2e` profile (no password is read here).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

case "${DOCKER_HOST:-}" in
  ""|unix://*) ;;
  *) echo "DOCKER_HOST points to a remote engine: refusing to purge." >&2; exit 1 ;;
esac
[ "$(docker inspect -f '{{.Name}} {{.State.Running}}' castor-db 2>/dev/null)" = "/castor-db true" ] \
  || { echo "castor-db is not a running local container: refusing to purge." >&2; exit 1; }

# Fixture accounts of the journeys (keep in sync with E2E_EMAILS in e2e/support/env.ts)
emails="e2e.owner@e2e.test,e2e.judge@e2e.test,e2e.athlete@e2e.test"

commit=""
[ "${1:-}" = "--apply" ] && commit=1
docker exec -i castor-db sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "${POSTGRES_DB:-$POSTGRES_USER}" "$@"' _ \
  -v "accounts=$emails" ${commit:+-v commit=$commit} < "$here/purge-e2e-data.sql"
