import {readFileSync} from 'node:fs';
import {join} from 'node:path';

const load = (lang: string): Record<string, unknown> =>
  JSON.parse(readFileSync(join(__dirname, '..', '..', 'src', 'assets', 'i18n', `${lang}.json`), 'utf8'));
const fr = load('fr');
const en = load('en');

const get = (tree: Record<string, unknown>, key: string): string => {
  const value = key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], tree);
  if (typeof value !== 'string') throw new Error(`Unknown i18n key ${key}`);
  return value;
};
const escape = (s: string): string => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * Matches a translated label in French or English, so a journey does not depend on the language
 * stored in the test account's preference. `{{param}}` placeholders match any text.
 */
export function t(key: string, opts: {exact?: boolean} = {}): RegExp {
  const pattern = (s: string): string => escape(s).replace(/\\\{\\\{\w+\\\}\\\}/g, '.*');
  const body = `(?:${pattern(get(en, key))}|${pattern(get(fr, key))})`;
  return new RegExp(opts.exact ? `^${body}$` : body, 'i');
}
