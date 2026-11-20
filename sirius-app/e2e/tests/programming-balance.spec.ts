import {asRole, expect, test} from '../support/fixtures';
import {t} from '../support/i18n';
import {createBoxSession} from '../support/sessions';
import {createBoxWod} from '../support/wods';

/*
 * Lot D': the Owner programs a session of the box this week and opens the session-order proposal
 * (nothing applied); a Coach reads the balance of the
 * programming (radar of the full week, the details of an axis, the RX / Scaled switch, the help);
 * an athlete has neither the entry nor the page. Only presence is checked: the box gathers the
 * sessions of every journey of the run.
 */
test('Owner programs → Coach reads the programming balance → athlete has no access', async ({browser}) => {
  const stamp = Date.now();
  const borrowed = `E2E prog borrowed ${stamp}`;

  const ownerCtx = await asRole(browser, 'owner');
  const owner = await ownerCtx.newPage();
  await createBoxWod(owner, {title: borrowed});
  await createBoxSession(owner, {title: `E2E prog ${stamp}`, borrowedWodTitle: borrowed, writtenWodTitle: `E2E prog skill ${stamp}`});

  // --- Owner (WOD_WRITE): the balance opens the session-order proposal ---
  await owner.goto('/box/programming');
  await owner.getByRole('link', {name: t('PROGRAMMING.ORDER.OPEN')}).click();
  await expect(owner).toHaveURL(/\/box\/programming\/order\?/);
  await expect(owner.locator('[data-slot="order-summary"]')).toBeVisible({timeout: 30_000});
  // Whole sessions: the strength-day option shows; nothing is written before a confirmation
  await owner.locator('[data-mode="WHOLE_SESSIONS"]').click();
  await expect(owner.getByRole('radio', {name: t('PROGRAMMING.ORDER.HEAVY_STRICT')})).toBeVisible();
  await expect(owner.locator('[data-slot="order-summary"]')).toBeVisible({timeout: 30_000});

  // --- Coach (judge): the entry of My box opens the balance of the current cycle ---
  const coachCtx = await asRole(browser, 'judge');
  const coach = await coachCtx.newPage();
  await coach.goto('/box/sessions');
  await coach.getByRole('link', {name: t('BOX.PROGRAMMING.TITLE')}).first().click();
  await expect(coach).toHaveURL(/\/box\/programming$/);
  const full = coach.locator('[data-slot="full-week"]');
  await expect(full).toBeVisible();
  await expect(coach.locator('[data-slot="cycle"]')).toBeVisible();
  await expect(full.getByRole('img', {name: t('PROGRAMMING.RADAR.LABEL')})).toBeVisible();

  // The written Skill is Air Squats: the Squat axis opens its examples, Core its four directions
  const axes = full.getByRole('group', {name: t('PROGRAMMING.RADAR.DETAILS')});
  await axes.getByRole('button', {name: t('MOVEMENT.PATTERNS.AXES.SQUAT')}).click();
  await expect(full.locator('[data-slot="radar-panel"]')).toContainText(t('PROGRAMMING.RADAR.EXAMPLES.SQUAT'));
  await axes.getByRole('button', {name: t('MOVEMENT.PATTERNS.AXES.CORE')}).click();
  await expect(full.locator('[data-slot="radar-panel"]')).toContainText(t('MOVEMENT.PATTERNS.CORE_LATERAL_FLEXION'));

  // The optional blocks are a switch that asks the balance again
  const optional = coach.waitForResponse(r => r.url().includes('/programming/gyms/') && r.url().includes('optional=true'));
  await coach.getByRole('switch', {name: t('PROGRAMMING.OPTIONAL')}).click();
  expect((await optional).status()).toBe(200);

  // Scaled reads the official Scaled versions: without any in the cycle (other journeys may add
  // some to the box) it stays disabled and a note says why
  const scaled = coach.getByRole('button', {name: 'Scaled', exact: true});
  if (await coach.locator('[data-slot="no-scaled"]').isVisible()) {
    await expect(scaled).toBeDisabled();
  } else {
    const balance = coach.waitForResponse(r => r.url().includes('/programming/gyms/') && r.url().includes('scaled=true'));
    await scaled.click();
    expect((await balance).status()).toBe(200);
    await expect(scaled).toHaveAttribute('aria-pressed', 'true');
  }

  await coach.getByRole('button', {name: t('PROGRAMMING.HELP.OPEN')}).click();
  await expect(coach.getByText(t('PROGRAMMING.HELP.ALERTS_TITLE'))).toBeVisible();
  await coach.keyboard.press('Escape');

  // --- Athlete: no entry, and the page sends back to My box ---
  const athleteCtx = await asRole(browser, 'athlete');
  const athlete = await athleteCtx.newPage();
  await athlete.goto('/box/sessions');
  await expect(athlete.getByRole('link', {name: t('BOX.LEADERBOARDS.TITLE')}).first()).toBeVisible();
  await expect(athlete.getByRole('link', {name: t('BOX.PROGRAMMING.TITLE')})).toHaveCount(0);
  await athlete.goto('/box/programming');
  await expect(athlete).not.toHaveURL(/\/box\/programming/);
  await athlete.goto('/box/programming/order');
  await expect(athlete).not.toHaveURL(/\/box\/programming/);

  await Promise.all([ownerCtx.close(), coachCtx.close(), athleteCtx.close()]);
});
