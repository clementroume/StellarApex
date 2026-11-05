import {execFileSync} from 'node:child_process';
import {join} from 'node:path';

/**
 * Deletes the E2E data (WODs, sessions, scores, registered accounts) of the local database through
 * e2e/cleanup/purge-e2e-data.sh. Scored WODs and sessions with a journal cannot be deleted from the UI (409).
 * `E2E_KEEP_DATA=1` keeps the data (to inspect a failure).
 */
export function purgeE2eData(): void {
  if (process.env['E2E_KEEP_DATA']) return;
  try {
    execFileSync('bash', [join(__dirname, '..', 'cleanup', 'purge-e2e-data.sh'), '--apply'], {stdio: 'pipe'});
  } catch (e) {
    console.warn(`E2E data not purged (docker / castor-db unreachable?): ${(e as Error).message.split('\n')[0]}`);
  }
}
