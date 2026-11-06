import {Browser, BrowserContext, test as base} from '@playwright/test';
import {authFile, Role} from './env';

/**
 * Traefik rate-limits the API per client IP (dynamic/routes.yml: 100 requests/min on Aldebaran) and every
 * browser of a run shares the IP of the machine. A 429 is not a defect of the journey: retry it a few times.
 * Only Aldebaran is intercepted: its responses set no cookie, whereas re-serving Antares responses
 * (`route.fulfill`) garbled the session / XSRF cookies and produced random 401s after sign-in steps.
 */
export async function retryOnRateLimit(context: BrowserContext): Promise<void> {
  await context.route(/\/aldebaran\//, async route => {
    try {
      for (let attempt = 0; ; attempt++) {
        const response = await route.fetch();
        if (response.status() !== 429 || attempt >= 8) {
          return await route.fulfill({response});
        }
        await new Promise(resolve => setTimeout(resolve, 1500));
      }
    } catch {
      // The context was closed while a call was in flight: nothing left to answer
    }
  });
}

/** A browser context signed in as `role` (saved session of the global setup), with the 429 retry. */
export async function asRole(browser: Browser, role: Role): Promise<BrowserContext> {
  const context = await browser.newContext({storageState: authFile(role), locale: 'en-US'});
  await retryOnRateLimit(context);
  return context;
}

export const test = base.extend<{ rateLimitRetry: void }>({
  rateLimitRetry: [async ({context}, use) => {
    await retryOnRateLimit(context);
    await use();
  }, {auto: true}]
});
export {expect} from '@playwright/test';
