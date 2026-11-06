import {asRole, expect, test} from '../support/fixtures';
import {t} from '../support/i18n';
import {expectScoreSaved, logTimeScore} from '../support/scores';
import {createBoxSession} from '../support/sessions';
import {createBoxWod} from '../support/wods';

test('Owner programs a session → Athlete does it and logs a score → blocks are locked for the Owner', async ({browser}) => {
  const stamp = Date.now();
  const title = `E2E session ${stamp}`;
  const borrowed = `E2E borrowed ${stamp}`;

  // --- Owner: a standalone box WOD, then a session (written Skill WOD + borrowed WOD) ---
  const ownerCtx = await asRole(browser, 'owner');
  const owner = await ownerCtx.newPage();
  await createBoxWod(owner, {title: borrowed});
  const sessionId = await createBoxSession(owner, {title, borrowedWodTitle: borrowed, writtenWodTitle: `E2E written ${stamp}`});

  // --- Athlete (in the project's viewport: phone-sized on "mobile") opens it ---
  const athleteCtx = await asRole(browser, 'athlete');
  const athlete = await athleteCtx.newPage();
  // (The week list shows 20 sessions at most and the box accumulates E2E sessions: open the session directly.)
  await athlete.goto(`/box/sessions/${sessionId}`);
  await expect(athlete.getByRole('heading', {name: title, level: 1})).toBeVisible();
  // Visible in advance: the whiteboard is readable before the session is done
  await expect(athlete.getByRole('article', {name: t('SESSION.KINDS.WOD', {exact: true})})).toBeVisible();

  // "Session done" opens the journal (every block ticked), then a score on the WOD block
  await athlete.getByRole('button', {name: t('SESSION.ACTIONS.LOG')}).click();
  await expect(athlete.getByRole('button', {name: t('SESSION.ACTIONS.LOG')})).toHaveCount(0);
  const wodBlock = athlete.getByRole('article', {name: t('SESSION.KINDS.WOD', {exact: true})});
  await logTimeScore(wodBlock, 6, 15);
  await expectScoreSaved(athlete);
  await expect(wodBlock.getByText(/0?6:15/)).toBeVisible();

  // --- Owner: the editor shows the scored block frozen and the checked Skill block undeletable ---
  await owner.goto(`/box/sessions/${sessionId}/edit`);
  const blocks = owner.locator('.session-block-editor');
  await expect(blocks).toHaveCount(2);
  await expect(blocks.nth(0).locator('.block-checked')).toBeVisible();
  await expect(blocks.nth(0).locator('.remove-block')).toHaveCount(0);
  await expect(blocks.nth(1).locator('.block-frozen')).toBeVisible();
  await expect(blocks.nth(1).locator('.remove-block')).toHaveCount(0);

  await ownerCtx.close();
  await athleteCtx.close();
});
