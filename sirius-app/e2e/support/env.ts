import {existsSync, readFileSync} from 'node:fs';
import {join} from 'node:path';

/** Roles of the journeys: Owner, judge (COACH with WOD_WRITE + SCORE_VERIFY) and athlete of the fixture box. */
export type Role = 'owner' | 'judge' | 'athlete';
export const ROLES: Role[] = ['owner', 'judge', 'athlete'];

/**
 * The journeys depend on no demo data: they sign in with the fixtures of Antares' `e2e` profile
 * (`E2eFixtureSeeder`: the box "E2E Box" with its three accounts), mirrored here. Local stack: the
 * password is read from Antares' `application-e2e.properties`; elsewhere set E2E_PASSWORD (and
 * activate the `e2e` profile there). Never run the journeys on a deployment that must stay clean:
 * they create WODs, sessions and scores (purged at the end).
 */
export const BOX_NAME = 'E2E Box';
export const BOX_CODE = 'E2EBOX';

export const E2E_EMAILS: Record<Role, string> = {
  owner: 'e2e.owner@e2e.test',
  judge: 'e2e.judge@e2e.test',
  athlete: 'e2e.athlete@e2e.test',
};

const E2E_PROPERTIES = join(__dirname, '..', '..', '..', 'antares-auth', 'src', 'main', 'resources', 'application-e2e.properties');

function e2ePassword(): string {
  const fromEnv = process.env['E2E_PASSWORD'];
  if (fromEnv) return fromEnv;
  const match = /^application\.e2e\.password=(.+)$/m.exec(existsSync(E2E_PROPERTIES) ? readFileSync(E2E_PROPERTIES, 'utf8') : '');
  if (!match) throw new Error(`No e2e password: set E2E_PASSWORD or application.e2e.password in ${E2E_PROPERTIES}.`);
  return match[1].trim();
}

export function credentials(role: Role): {email: string; password: string} {
  return {email: E2E_EMAILS[role], password: e2ePassword()};
}

export const authFile = (role: Role): string => join(__dirname, '..', '.auth', `${role}.json`);
