import {DEFAULT_RETURN_URL, forwardableReturnUrl, resolveReturnUrl} from './return-url';

describe('resolveReturnUrl', () => {
  const host = 'stellar.apex';

  it('should keep an app path with its query string', () => {
    expect(resolveReturnUrl('/my-account/gyms/join?gym=4&code=TESTBOX', host))
      .toEqual({kind: 'internal', url: '/my-account/gyms/join?gym=4&code=TESTBOX'});
  });

  it('should accept the current host and its subdomains as external targets', () => {
    expect(resolveReturnUrl('https://docs.stellar.apex/swagger', host))
      .toEqual({kind: 'external', url: 'https://docs.stellar.apex/swagger'});
    expect(resolveReturnUrl('https://stellar.apex/today', host).kind).toBe('external');
  });

  it.each([
    'https://evil.com', 'https://stellar.apex.evil.com', 'https://evilstellar.apex', '//evil.com',
    '/\\evil.com', 'javascript:alert(1)', 'today', '', null, undefined
  ])('should fall back to /today for %s', raw => {
    expect(resolveReturnUrl(raw, host)).toEqual({kind: 'internal', url: DEFAULT_RETURN_URL});
  });
});

describe('forwardableReturnUrl', () => {
  it('should only forward checked app paths other than the default', () => {
    expect(forwardableReturnUrl('/join?gym=4&code=X', 'localhost')).toBe('/join?gym=4&code=X');
    expect(forwardableReturnUrl('/today', 'localhost')).toBeNull();
    expect(forwardableReturnUrl('https://docs.localhost/', 'localhost')).toBeNull();
    expect(forwardableReturnUrl('https://evil.com', 'localhost')).toBeNull();
  });
});
