import {asRole, expect, test} from '../support/fixtures';
import type {Role} from '../support/env';
import {t} from '../support/i18n';
import {expectScoreSaved, logTimeScore} from '../support/scores';
import {createBoxWod} from '../support/wods';

test('box WOD → two scores → leaderboard order → scored WOD is locked', async ({browser}) => {
  const title = `E2E WOD ${Date.now()}`;
  const as = async (role: Role) => {
    const context = await asRole(browser, role);
    return {context, page: await context.newPage()};
  };

  // Owner writes the box WOD
  const owner = await as('owner');
  const wodId = await createBoxWod(owner.page, {title});

  // Athlete (slower) then judge (faster) each log a time
  const athlete = await as('athlete');
  await athlete.page.goto(`/wods/${wodId}`);
  await logTimeScore(athlete.page, 5, 30);
  await expectScoreSaved(athlete.page);

  const judge = await as('judge');
  await judge.page.goto(`/wods/${wodId}`);
  await logTimeScore(judge.page, 4, 10);
  await expectScoreSaved(judge.page);

  // Leaderboard: fastest first
  await owner.page.goto(`/wods/${wodId}`);
  const rows = owner.page.getByRole('region', {name: t('SCORE.LEADERBOARD.TITLE')}).getByRole('listitem');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0)).toContainText(/0?4:10/);
  await expect(rows.nth(1)).toContainText(/0?5:30/);

  // A WOD with logged scores is locked: no Edit link, and the edit URL bounces back with an error
  await expect(owner.page.getByRole('link', {name: t('GLOBAL.EDIT'), exact: true})).toHaveCount(0);
  await owner.page.goto(`/wods/edit/${wodId}`);
  await expect(owner.page).toHaveURL(new RegExp(`/wods/${wodId}$`));
  await expect(owner.page.getByText(t('WOD.MESSAGES.LOCKED'))).toBeVisible();

  await owner.context.close();
  await athlete.context.close();
  await judge.context.close();
});
