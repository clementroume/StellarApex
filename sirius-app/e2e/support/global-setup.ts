import {chromium, FullConfig} from '@playwright/test';
import {mkdirSync} from 'node:fs';
import {dirname} from 'node:path';
import {authFile, credentials, ROLES} from './env';
import {purgeE2eData} from './purge';

/** Purges the leftovers of a failed run, then signs every role in once and keeps its session (cookies) in e2e/.auth/<role>.json. */
export default async function globalSetup(config: FullConfig): Promise<void> {
  purgeE2eData();
  const baseURL = config.projects[0].use.baseURL ?? 'http://localhost:4200';
  const browser = await chromium.launch();
  try {
    for (const role of ROLES) {
      const {email, password} = credentials(role); // fails fast with a clear message
      const context = await browser.newContext({baseURL, locale: 'en-US'});
      const page = await context.newPage();
      await page.goto('/auth/login');
      await page.locator('#login-email').fill(email);
      await page.locator('#login-password').fill(password);
      await page.locator('form button[type=submit]').click();
      await page.waitForURL(url => !url.pathname.startsWith('/auth'), {timeout: 20_000}).catch(() => {
        throw new Error(`Sign-in of the ${role} test account failed (check E2E_${role.toUpperCase()}_* in e2e/local.env).`);
      });
      mkdirSync(dirname(authFile(role)), {recursive: true});
      await context.storageState({path: authFile(role)});
      await context.close();
    }
  } finally {
    await browser.close();
  }
}
