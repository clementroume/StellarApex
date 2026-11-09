import {asRole, expect, test} from '../support/fixtures';
import {t} from '../support/i18n';
import {expectScoreSaved} from '../support/scores';

/*
 * Lot C: a strength WOD written as a percentage, sets logged set by set, the 3RM record and the
 * estimated 1RM on "My records", then the percentage turned into the athlete's load. Uses a variant
 * the test accounts never lift elsewhere (Zercher Squat, a Squat with its usual name), so the numbers
 * do not depend on other data.
 */
test('percentage WOD → sets → 3RM record and estimated 1RM → lift page → load from the estimate', async ({browser}) => {
  const title = `E2E Records ${Date.now()}`;
  const context = await asRole(browser, 'athlete');
  const page = await context.newPage();

  // Personal For Load WOD: 3x3 Zercher Squat @ 50% of the 1RM
  await page.goto('/training/wods/new');
  await page.getByRole('button', {name: 'For Load', exact: true}).click();
  await page.locator('#quick-sets').fill('3x3');
  await page.getByRole('combobox', {name: t('WOD.FIELDS.MOVEMENT')}).first().fill('Zercher Squat');
  await page.getByRole('option', {name: /^Zercher Squat\b/}).first().click();
  await page.getByRole('spinbutton', {name: t('WOD.QUICK.PERCENT')}).fill('50');
  await page.locator('#quick-title').fill(title);
  await page.locator('#quick-submit').click();
  await expect(page).toHaveURL(/\/wods\/\d+$/);
  const wodUrl = page.url();

  // The percentage is on the whiteboard (the desktop and mobile runs share the athlete: no "no record" check)
  await expect(page.locator('[data-slot="percent-source"]')).toContainText('50');

  // Three sets of 3: 100, 110, 120 kg → 3RM 120 kg, Epley 1RM 132 kg
  await page.getByRole('button', {name: t('SCORE.ACTIONS.LOG')}).first().click();
  const dialog = page.getByRole('dialog', {name: t('SCORE.TITLES.LOG')});
  await dialog.getByRole('button', {name: t('SCORE.SETS.START')}).click();
  for (const [i, kg] of [100, 110, 120].entries()) {
    if (i > 0) await dialog.getByRole('button', {name: t('SCORE.SETS.ADD')}).click();
    await dialog.getByRole('spinbutton', {name: t('SCORE.SETS.REPS')}).nth(i).fill('3');
    await dialog.getByRole('spinbutton', {name: t('SCORE.SETS.WEIGHT')}).nth(i).fill(String(kg));
  }
  await dialog.getByRole('button', {name: t('GLOBAL.SAVE')}).click();
  await expectScoreSaved(page);

  // My records: the Zercher Squat tile shows the estimated 1RM, the lift page the 3RM record
  await page.goto('/training/records');
  const tile = page.locator('[data-slot="lift-tile"]', {hasText: 'Zercher Squat'});
  await expect(tile).toContainText('≈ 132');
  await tile.click();
  await expect(page).toHaveURL(/\/training\/records\/\d+\?variant=/);
  await expect(page.locator('[data-slot="rm-table"]')).toContainText(/3RM\s*120 kg/);
  await expect(page.locator('[data-slot="percent-table"]')).toContainText('132 kg');

  // The WOD now gives the athlete's load: 50% of the estimated 1RM
  await page.goto(wodUrl);
  await expect(page.locator('[data-slot="percent"]')).toContainText('≈ 66 kg');

  await context.close();
});
