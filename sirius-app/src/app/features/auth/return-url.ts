/** Where to go after signing in or up when no (valid) returnUrl is given. */
export const DEFAULT_RETURN_URL = '/today';

/** A checked destination: an app path for the router, or an absolute URL for the browser. */
export type ReturnTarget = {kind: 'internal'; url: string} | {kind: 'external'; url: string};

/**
 * Validates a `returnUrl` query parameter so sign-in and sign-up never redirect anywhere else
 * (no open redirect). Accepted:
 * - an app path (`/my-account/gyms/join?gym=4&code=X`), never `//host` or `/\host`;
 * - an http(s) URL on the current host or one of its subdomains (the admin tools behind Traefik,
 *   e.g. `https://docs.stellar.apex/...` while the app runs on `stellar.apex`).
 * Anything else falls back to `/today`.
 */
export function resolveReturnUrl(raw: string | null | undefined, currentHost: string): ReturnTarget {
  const value = (raw ?? '').trim();
  if (value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\')) {
    return {kind: 'internal', url: value};
  }
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    const current = currentHost.toLowerCase();
    if ((url.protocol === 'https:' || url.protocol === 'http:') && (host === current || host.endsWith('.' + current))) {
      return {kind: 'external', url: url.href};
    }
  } catch {
    // Not an absolute URL: fall back below
  }
  return {kind: 'internal', url: DEFAULT_RETURN_URL};
}

/** The returnUrl to carry between the sign-in and sign-up pages, only when it is a checked app path. */
export function forwardableReturnUrl(raw: string | null | undefined, currentHost: string): string | null {
  const target = resolveReturnUrl(raw, currentHost);
  return target.kind === 'internal' && target.url !== DEFAULT_RETURN_URL ? target.url : null;
}
