import {asRole, expect, test} from '../support/fixtures';
import {BOX_CODE, BOX_NAME} from '../support/env';
import {t} from '../support/i18n';
import {firstLink, sirius, waitForMail} from '../support/mailpit';

// One registration per run (the backend throttles them per IP): desktop project only (skip in the test).
test.use({storageState: {cookies: [], origins: []}});

test('register → unverified cannot join → verify by e-mail → join the fixture box → Owner validates', async ({page, browser}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'one registration per run');
  const stamp = Date.now();
  const email = `e2e+${stamp}@test.local`; // pattern purged by e2e/cleanup
  const password = `E2e-${stamp}-Pw`;
  const name = `E2E${stamp}`;

  // --- Registration ---
  await page.goto('/auth/register');
  await page.locator('#register-first-name').fill(name);
  await page.locator('#register-last-name').fill('Runner');
  await page.locator('#register-email').fill(email);
  await page.locator('#register-password').fill(password);
  await page.locator('#register-confirmation').fill(password);
  await page.getByRole('button', {name: t('REGISTER.SIGN_UP')}).click();
  await expect(page).not.toHaveURL(/\/auth\//);

  try {
    // --- Unverified: cannot join a box ---
    await page.goto('/my-account/gyms/join');
    await expect(page.getByText(t('EMAIL_VERIFICATION.TITLE'))).toBeVisible();
    await page.getByPlaceholder(t('JOIN_GYM.SEARCH')).fill(BOX_NAME);
    await page.locator('[data-slot=gym-result]', {hasText: BOX_NAME}).first().click();
    await page.locator('#enrollment-code').fill(BOX_CODE);
    let status = 0;
    await expect(async () => { // Antares writes are not retried by the 429 fixture: click again when rate-limited
      const answered = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/antares/gyms/join') && r.status() !== 401); // a 401 is refreshed and retried by the app
      await page.getByRole('button', {name: t('JOIN_GYM.SUBMIT'), exact: true}).click();
      status = (await answered).status();
      expect(status).not.toBe(429);
    }).toPass({timeout: 30_000});
    expect(status).toBe(403); // EMAIL_NOT_VERIFIED
    await expect(page.getByText(t('JOIN_GYM.JOINED'))).toHaveCount(0);
    await expect(page.locator('[data-slot=join-form]')).toBeVisible(); // still on the form

    // --- Verification through the mailed link (Mailpit) ---
    const mail = await waitForMail(email);
    await page.goto(sirius(firstLink(mail, 'verify-email')));
    await expect(page.getByRole('heading', {name: t('VERIFY_EMAIL.VERIFIED_TITLE')})).toBeVisible();

    // --- Join the fixture box with its code ---
    await page.goto('/my-account/gyms/join');
    await expect(page.getByText(t('EMAIL_VERIFICATION.TITLE'))).toHaveCount(0);
    await page.getByPlaceholder(t('JOIN_GYM.SEARCH')).fill(BOX_NAME);
    await page.locator('[data-slot=gym-result]', {hasText: BOX_NAME}).first().click();
    await page.locator('#enrollment-code').fill(BOX_CODE);
    await page.getByRole('button', {name: t('JOIN_GYM.SUBMIT'), exact: true}).click();
    // The fixture box has no auto-subscription: the request waits for the Owner
    await expect(page.getByText(t('JOIN_GYM.REQUESTED'))).toBeVisible();

    // --- The Owner validates the request ---
    const owner = await asRole(browser, 'owner');
    const ownerPage = await owner.newPage();
    await ownerPage.goto('/box/members');
    const row = ownerPage.locator('[data-slot=member]', {hasText: email});
    await expect(row).toBeVisible();
    await expect(row.getByText(t('GYM.STATUS.PENDING'))).toBeVisible();
    await row.getByRole('button', {name: t('BOX.MEMBERS.VALIDATE')}).click();
    await expect(row.getByText(t('GYM.STATUS.PENDING'))).toHaveCount(0);
    await owner.close();

    // --- The athlete sees the box ---
    await page.goto('/my-account/gyms');
    const mine = page.locator('[data-slot=my-gym]', {hasText: BOX_NAME});
    await expect(mine).toBeVisible();
    await expect(mine.getByText(t('GYM.STATUS.PENDING'))).toHaveCount(0);
  } finally {
    // The account does not stay in the database: delete it from its own settings
    await page.goto('/my-account/settings');
    if (await page.waitForURL(/\/(auth\/login|my-account\/settings)/).then(() => page.url().includes('/auth/login'))) { // session lost: sign in again to delete the account
      await page.locator('#login-email').fill(email);
      await page.locator('#login-password').fill(password);
      await page.getByRole('button', {name: t('LOGIN.SIGN_IN')}).click();
      await expect(page).toHaveURL(/\/my-account\/settings/);
    }
    await page.getByRole('button', {name: t('SETTINGS.DELETE_ACCOUNT_BTN')}).click();
    await page.getByRole('button', {name: t('SETTINGS.DELETE_MODAL_CONFIRM')}).click();
    await expect(page).toHaveURL(/\/auth\//); // signed out, sent to the registration page
  }
});
