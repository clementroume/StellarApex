import {expect, test} from '../support/fixtures';
import {credentials} from '../support/env';
import {t} from '../support/i18n';

// Anonymous session: no stored state.
test.use({storageState: {cookies: [], origins: []}});

test('protected deep link → sign-in → back to the returnUrl → sign-out', async ({page}) => {
  const {email, password} = credentials('athlete');

  await page.goto('/training');
  await expect(page).toHaveURL(/\/auth\/login\?returnUrl=%2Ftraining/);

  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.getByRole('button', {name: t('LOGIN.SIGN_IN')}).click();

  await expect(page).toHaveURL(/\/training(\/|$|\?)/);

  await page.getByRole('button', {name: t('NAVBAR.LOG_OUT')}).click();
  await expect(page).toHaveURL(/\/auth\/login/);

  // The session is really gone: a protected page sends us back to the sign-in.
  await page.goto('/training');
  await expect(page).toHaveURL(/\/auth\/login/);
});
