import {asRole, expect, test} from '../support/fixtures';
import {t} from '../support/i18n';
import {expectScoreSaved} from '../support/scores';

/*
 * Lot D: a strength score logged set by set shows on the weekly balance of the current week: the
 * lift in the strength ring and close to the record (the sets prove the record of their day), the
 * thigh and leg region worked, its muscles one tap away. Only presence is checked: the desktop and
 * mobile runs share the athlete.
 */
test('strength sets → weekly balance: lift share, close to the record, region muscles', async ({browser}) => {
  const title = `E2E Balance ${Date.now()}`;
  const context = await asRole(browser, 'athlete');
  const page = await context.newPage();

  await page.goto('/training/wods/new');
  await page.getByRole('button', {name: 'For Load', exact: true}).click();
  await page.locator('#quick-sets').fill('3x3');
  await page.getByRole('combobox', {name: t('WOD.FIELDS.MOVEMENT')}).first().fill('Zercher Squat');
  await page.getByRole('option', {name: /^Zercher Squat\b/}).first().click();
  await page.locator('#quick-title').fill(title);
  await page.locator('#quick-submit').click();
  await expect(page).toHaveURL(/\/wods\/\d+$/);

  await page.getByRole('button', {name: t('SCORE.ACTIONS.LOG')}).first().click();
  const dialog = page.getByRole('dialog', {name: t('SCORE.TITLES.LOG')});
  await dialog.getByRole('button', {name: t('SCORE.SETS.START')}).click();
  for (const [i, kg] of [80, 90, 100].entries()) {
    if (i > 0) await dialog.getByRole('button', {name: t('SCORE.SETS.ADD')}).click();
    await dialog.getByRole('spinbutton', {name: t('SCORE.SETS.REPS')}).nth(i).fill('3');
    await dialog.getByRole('spinbutton', {name: t('SCORE.SETS.WEIGHT')}).nth(i).fill(String(kg));
  }
  await dialog.getByRole('button', {name: t('GLOBAL.SAVE')}).click();
  await expectScoreSaved(page);

  // The current week: the squat in the strength ring, close to the record
  await page.goto('/training/review');
  await expect(page.getByText(t('REVIEW.CURRENT'), {exact: true})).toBeVisible();
  await expect(page.getByRole('button', {name: t('REVIEW.NEXT_WEEK')})).toBeDisabled();
  await expect(page.locator('[data-slot="force-legend"]')).toContainText('Squat');
  await expect(page.locator('[data-slot="near-record"]')).toContainText('Squat');

  // The thigh and leg region opens its muscles right under its bar
  const region = page.locator('[data-slot="region-bars"] button').filter({hasText: t('MUSCLE.REGIONS.THIGH_LEG')});
  await region.click();
  await expect(region).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('[data-slot="muscles-THIGH_LEG"] a').first()).toBeVisible();

  // One help for the whole screen, next to the title
  await page.getByRole('button', {name: t('REVIEW.HELP.OPEN')}).click();
  await expect(page.getByText(t('REVIEW.HELP.WINDOW'))).toBeVisible();

  await context.close();
});

test('a line text the balance cannot read is flagged in the WOD form, without blocking', async ({browser}) => {
  const context = await asRole(browser, 'athlete');
  const page = await context.newPage();
  await page.goto('/wods/new');
  await page.getByRole('button', {name: t('WOD.ACTIONS.ADD_MOVEMENT')}).click();
  await page.getByRole('combobox', {name: t('WOD.FIELDS.MOVEMENT')}).fill('Air Squat');
  await page.getByRole('option', {name: /^Air Squat\b/}).first().click();
  const reps = page.getByRole('textbox', {name: t('WOD.FIELDS.REPS'), exact: false}).first();
  await reps.fill('10 each side');
  await expect(page.getByText(t('WOD.REPS_UNREADABLE'))).toBeVisible();
  await reps.fill('21-15-9');
  await expect(page.getByText(t('WOD.REPS_UNREADABLE'))).toBeHidden();
  await context.close();
});

