import {expect, Locator, Page} from '@playwright/test';
import {t} from './i18n';

/** Logs a time score (opens the "Log my score" dialog) from the WOD page or from a session block. */
export async function logTimeScore(scope: Page | Locator, minutes: number, seconds: number): Promise<void> {
  await scope.getByRole('button', {name: t('SCORE.ACTIONS.LOG')}).first().click();
  const dialog = scope.getByRole('dialog', {name: t('SCORE.TITLES.LOG')});
  await dialog.getByRole('spinbutton', {name: t('SCORE.FIELDS.MINUTES')}).fill(String(minutes));
  await dialog.getByRole('spinbutton', {name: t('SCORE.FIELDS.SECONDS')}).fill(String(seconds));
  return dialog.getByRole('button', {name: t('GLOBAL.SAVE')}).click();
}

export async function expectScoreSaved(page: Page): Promise<void> {
  await expect(page.getByRole('dialog', {name: t('SCORE.TITLES.LOG')})).toHaveCount(0);
}
