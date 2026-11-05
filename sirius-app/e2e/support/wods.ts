import {expect, Page} from '@playwright/test';
import {t} from './i18n';

export interface BoxWodOptions {
  title: string;
  /** Competition WOD of the box (never public): staff verify the scores. */
  competition?: boolean;
}

/**
 * Creates a For Time / Time WOD of the box (full form, `/wods/new`) with one movement and returns its id.
 * The signed-in user needs WOD_WRITE on the box (Owner).
 */
export async function createBoxWod(page: Page, {title, competition = false}: BoxWodOptions): Promise<number> {
  await page.goto('/wods/new');
  await page.getByRole('textbox', {name: t('WOD.FIELDS.TITLE')}).first().fill(title);
  await page.getByRole('combobox', {name: t('WOD.FIELDS.FORMAT')}).selectOption({label: 'For Time'});
  await page.getByRole('combobox', {name: t('WOD.FIELDS.SCORE_TYPE')}).selectOption({label: 'Time'});
  if (competition) {
    await page.getByRole('switch', {name: t('WOD.FIELDS.COMPETITION')}).check();
  }

  await page.getByRole('button', {name: t('WOD.ACTIONS.ADD_MOVEMENT')}).click();
  await page.getByRole('combobox', {name: t('WOD.FIELDS.MOVEMENT')}).fill('Air Squat');
  await page.getByRole('option', {name: /^Air Squat\b/}).first().click();
  await page.getByRole('textbox', {name: t('WOD.FIELDS.REPS'), exact: false}).first().fill('10');

  await page.getByRole('button', {name: t('GLOBAL.SAVE')}).click();
  await expect(page).toHaveURL(/\/wods\/\d+$/);
  return Number(page.url().split('/').pop());
}
