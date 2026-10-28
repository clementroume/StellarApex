/**
 * Reads the single-use token of a mailed link (`/auth/reset-password#token=...`). The token travels
 * in the URL fragment, which browsers never send to a server; once read, it is removed from the
 * address bar and history so it does not linger there.
 */
export function readLinkToken(document: Document): string | null {
  const fragment = document.location.hash.replace(/^#/, '');
  const token = new URLSearchParams(fragment).get('token');
  if (token) {
    try {
      document.defaultView?.history.replaceState(
        document.defaultView.history.state, '', document.location.pathname + document.location.search);
    } catch {
      // History unavailable (tests, sandboxed frame): the token simply stays in the address bar
    }
  }
  return token && /^[A-Za-z0-9_-]{20,100}$/.test(token) ? token : null;
}
