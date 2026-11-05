#!/usr/bin/env bash
# Deletes the data created by the e2e journeys (see purge-e2e-data.sql). LOCAL Docker stack only:
# refuses to run unless the running container is the local `castor-db`. Dry run by default; `--apply` commits.
# Test accounts come from E2E_*_EMAIL (environment or e2e/local.env); no password is read.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

[ "$(docker inspect -f '{{.Name}} {{.State.Running}}' castor-db 2>/dev/null)" = "/castor-db true" ] \
  || { echo "castor-db is not a running local container: refusing to purge." >&2; exit 1; }

emails=""
for role in OWNER JUDGE ATHLETE PROGRAMMER; do
  var="E2E_${role}_EMAIL"
  value="${!var-}"
  if [ -z "$value" ] && [ -f "$here/../local.env" ]; then
    value="$(sed -n "s/^E2E_${role}_EMAIL=//p" "$here/../local.env" | head -1)"
  fi
  [ -n "$value" ] || { echo "Missing E2E_${role}_EMAIL." >&2; exit 1; }
  emails="${emails:+$emails,}$value"
done

commit=""
[ "${1:-}" = "--apply" ] && commit=1
docker exec -i castor-db sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "${POSTGRES_DB:-$POSTGRES_USER}" "$@"' _ \
  -v "accounts=$emails" ${commit:+-v commit=$commit} < "$here/purge-e2e-data.sql"
