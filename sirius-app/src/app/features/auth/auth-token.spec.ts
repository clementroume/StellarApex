import {readLinkToken} from './auth-token';

describe('readLinkToken', () => {
  const doc = (hash: string): Document => {
    const replaceState = vi.fn().mockName('history.replaceState');
    return {
      location: {hash, pathname: '/auth/reset-password', search: ''},
      defaultView: {history: {state: null, replaceState}}
    } as unknown as Document;
  };

  it('should read the token of the fragment and remove it from the address bar', () => {
    const document = doc('#token=AbC_123-xyzAbC_123-xyz');

    expect(readLinkToken(document)).toBe('AbC_123-xyzAbC_123-xyz');
    expect(document.defaultView!.history.replaceState).toHaveBeenCalledWith(null, '', '/auth/reset-password');
  });

  it('should refuse a missing or malformed token', () => {
    expect(readLinkToken(doc(''))).toBeNull();
    expect(readLinkToken(doc('#token=short'))).toBeNull();
    expect(readLinkToken(doc('#token=<script>alert(1)</script>xxxxxxxxxx'))).toBeNull();
  });
});
