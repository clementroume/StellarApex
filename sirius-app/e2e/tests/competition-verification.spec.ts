import {asRole, expect, test} from '../support/fixtures';
import {t} from '../support/i18n';
import {expectScoreSaved, logTimeScore} from '../support/scores';
import {createBoxSession, wodIdOfWodBlock} from '../support/sessions';

test('competition WOD of a session → Athlete submits → judge verifies from the queue → score locked for the Athlete', async ({browser}) => {
  const stamp = Date.now();
  const title = `E2E competition ${stamp}`;

  // --- Owner: competition WOD of the box, programmed in a session (where the judges' queue lives) ---
  const ownerCtx = await asRole(browser, 'owner');
  const owner = await ownerCtx.newPage();
  const sessionId = await createBoxSession(owner, {title: `E2E comp session ${stamp}`, competitionWodTitle: title, writtenWodTitle: `E2E skill ${stamp}`});
  await expect(owner.getByText(t('WOD.FIELDS.COMPETITION', {exact: true}))).toBeVisible();
  const wodId = await wodIdOfWodBlock(owner);
  await ownerCtx.close();

  // --- Athlete logs a time and submits it for verification ---
  const athleteCtx = await asRole(browser, 'athlete');
  const athlete = await athleteCtx.newPage();
  await athlete.goto(`/wods/${wodId}`);
  await athlete.getByRole('button', {name: t('SCORE.ACTIONS.LOG')}).first().click();
  const dialog = athlete.getByRole('dialog', {name: t('SCORE.TITLES.LOG')});
  await dialog.getByRole('spinbutton', {name: t('SCORE.FIELDS.MINUTES')}).fill('7');
  await dialog.getByRole('spinbutton', {name: t('SCORE.FIELDS.SECONDS')}).fill('45');
  await dialog.getByRole('checkbox', {name: t('SCORE.FIELDS.SUBMIT')}).check();
  await dialog.getByRole('button', {name: t('GLOBAL.SAVE')}).click();
  await expectScoreSaved(athlete);
  await athlete.getByText(t('SCORE.HISTORY')).click(); // folded "My history"
  await expect(athlete.getByText(t('SCORE.SUBMITTED'))).toBeVisible();
  // Not verified yet: the athlete can still change it
  await expect(athlete.getByRole('button', {name: t('GLOBAL.EDIT', {exact: true})})).toBeVisible();

  // --- Judge verifies it from the queue of the session block ---
  const judgeCtx = await asRole(browser, 'judge');
  const judge = await judgeCtx.newPage();
  await judge.goto(`/box/sessions/${sessionId}`);
  const queue = judge.getByRole('region', {name: t('SESSION.QUEUE.TITLE')});
  const entry = queue.getByRole('listitem').filter({hasText: /0?7:45/});
  await expect(entry).toBeVisible();
  await entry.getByRole('button', {name: t('SCORE.ACTIONS.VERIFY')}).click();
  await expect(queue.getByRole('listitem').filter({hasText: /0?7:45/})).toHaveCount(0);
  await judgeCtx.close();

  // --- The verified score is locked for its athlete ---
  await athlete.reload();
  await athlete.getByText(t('SCORE.HISTORY')).click();
  await expect(athlete.getByText(t('SCORE.VERIFIED_BY'))).toBeVisible();
  await expect(athlete.getByRole('button', {name: t('GLOBAL.EDIT', {exact: true})})).toHaveCount(0);
  await expect(athlete.getByRole('button', {name: t('GLOBAL.DELETE', {exact: true})})).toHaveCount(0);
  await athleteCtx.close();
});
