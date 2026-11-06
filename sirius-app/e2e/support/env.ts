import {existsSync, readFileSync} from 'node:fs';
import {join} from 'node:path';

export type Role = 'owner' | 'judge' | 'athlete' | 'programmer';
export const ROLES: Role[] = ['owner', 'judge', 'athlete', 'programmer'];

/** Loads e2e/local.env into process.env (already-set variables win). */
export function loadLocalEnv(): void {
  const file = join(__dirname, '..', 'local.env');
  if (!existsSync(file)) return;
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = /^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/.exec(line);
    if (m && !line.trim().startsWith('#') && process.env[m[1]] === undefined) process.env[m[1]] = m[2];
  }
}

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing ${name}. Copy sirius-app/e2e/local.env.example to local.env and fill it in (README, "End-to-end tests").`);
  }
  return value;
}

export function credentials(role: Role): {email: string; password: string} {
  const prefix = `E2E_${role.toUpperCase()}`;
  return {email: required(`${prefix}_EMAIL`), password: required(`${prefix}_PASSWORD`)};
}

export function boxCode(): string {
  return required('E2E_BOX_CODE');
}

export const authFile = (role: Role): string => join(__dirname, '..', '.auth', `${role}.json`);
