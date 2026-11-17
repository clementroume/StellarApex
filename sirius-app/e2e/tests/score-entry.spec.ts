import {asRole, expect, test} from '../support/fixtures';
import type {Role} from '../support/env';
import {t} from '../support/i18n';
import {expectScoreSaved, logTimeScore} from '../support/scores';
import {createBoxWod} from '../support/wods';

test('official Scaled version → RX, official Scaled and own Scaled scores → only RX and official are ranked', async ({browser}) => {
  const title = `E2E Scaled ${Date.now()}`;
  const as = async (role: Role) => {
    const context = await asRole(browser, role);
    return {context, page: await context.newPage()};
  };

  // Owner writes the box WOD with its official Scaled version: Jumping Jacks in place of the Air Squats
  const owner = await as('owner');
  const wodId = await createBoxWod(owner.page, {title, scaledMovement: 'Jumping Jack'});
  await expect(owner.page.getByRole('group', {name: t('WOD.SCALED.TITLE')})).toBeVisible();

  // Owner: RX
  await logTimeScore(owner.page, 4, 0);
  await expectScoreSaved(owner.page);

  // Athlete: the official Scaled version, its line prefilled
  const athlete = await as('athlete');
  await athlete.page.goto(`/wods/${wodId}`);
  await athlete.page.getByRole('button', {name: t('SCORE.ACTIONS.LOG')}).first().click();
  const dialog = athlete.page.getByRole('dialog', {name: t('SCORE.TITLES.LOG')});
  await dialog.getByRole('button', {name: t('SCORE.SCALING.OFFICIAL')}).click();
  await expect(dialog.getByText(/Jumping Jacks?/).first()).toBeVisible();
  await dialog.getByRole('spinbutton', {name: t('SCORE.FIELDS.MINUTES')}).fill('5');
  await dialog.getByRole('spinbutton', {name: t('SCORE.FIELDS.SECONDS')}).fill('0');
  await dialog.getByRole('button', {name: t('GLOBAL.SAVE')}).click();
  await expectScoreSaved(athlete.page);

  // Judge: a Scaled of one's own, fewer reps on the line
  const judge = await as('judge');
  await judge.page.goto(`/wods/${wodId}`);
  await judge.page.getByRole('button', {name: t('SCORE.ACTIONS.LOG')}).first().click();
  const own = judge.page.getByRole('dialog', {name: t('SCORE.TITLES.LOG')});
  await own.getByRole('button', {name: t('SCORE.SCALING.OWN')}).click();
  await own.getByRole('button', {name: t('SCORE.LINES.CHANGE')}).click();
  await own.getByRole('textbox', {name: t('SCORE.LINES.REPS')}).fill('5');
  await own.getByRole('spinbutton', {name: t('SCORE.FIELDS.MINUTES')}).fill('3');
  await own.getByRole('spinbutton', {name: t('SCORE.FIELDS.SECONDS')}).fill('0');
  await own.getByRole('button', {name: t('GLOBAL.SAVE')}).click();
  await expectScoreSaved(judge.page);
  // Its history tells the line done
  await judge.page.getByText(t('SCORE.HISTORY')).click();
  await expect(judge.page.locator('.score-line').first()).toContainText('5');

  // Leaderboards: RX = the owner only; Scaled = the official version only (the judge's own Scaled is never ranked)
  await owner.page.goto(`/wods/${wodId}`);
  const board = owner.page.getByRole('region', {name: t('SCORE.LEADERBOARD.TITLE')});
  await expect(board.getByRole('listitem')).toHaveCount(1);
  await board.getByRole('button', {name: t('SCORE.SCALING.OFFICIAL')}).click();
  await expect(board.getByRole('listitem')).toHaveCount(1);
  await expect(board.getByRole('listitem').first()).toContainText(/0?5:00/);

  await owner.context.close();
  await athlete.context.close();
  await judge.context.close();
});
