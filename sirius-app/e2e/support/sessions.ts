import {expect, Locator, Page} from '@playwright/test';
import {t} from './i18n';

export interface BoxSessionOptions {
  /** Unique title: how the athlete finds the session in the week. */
  title: string;
  /** Name of the WOD written in the "Skill" block. */
  writtenWodTitle: string;
  /** Standalone WOD of the box borrowed by the "WOD" block... */
  borrowedWodTitle?: string;
  /** ...or a WOD written in the "WOD" block and flagged as a competition (then not borrowed). */
  competitionWodTitle?: string;
}

/** Writes the WOD of a block with the quick editor (opens its dialog from the block's "Write the WOD"). */
async function writeWod(page: Page, block: Locator, title: string): Promise<void> {
  await block.locator('.write-wod').click();
  const editor = page.getByRole('dialog', {name: t('SESSION.FORM.WRITE_WOD')});
  await editor.getByRole('combobox', {name: t('WOD.FIELDS.MOVEMENT')}).fill('Air Squat');
  await page.getByRole('option', {name: /^Air Squat\b/}).first().click();
  await editor.getByRole('textbox', {name: t('WOD.QUICK.REPS'), exact: true}).fill('10');
  await editor.locator('#quick-title').fill(title);
  await editor.locator('#quick-submit').click();
  await expect(block.getByText(title)).toBeVisible();
}

/**
 * Owner flow: a dated WOD session of the box (today), visible in advance, with a written Skill WOD and
 * either a borrowed WOD or a written competition WOD. Returns the session id.
 */
export async function createBoxSession(page: Page, o: BoxSessionOptions): Promise<number> {
  await page.goto('/box/sessions/new');
  await page.locator('#edit-visibility').click();
  await page.locator('#session-visibility').selectOption('ADVANCE');
  await page.locator('#show-more').click();
  await page.locator('#session-title').fill(o.title);

  const blocks = page.locator('.session-block-editor');
  const [skill, wod] = [blocks.nth(0), blocks.nth(1)];
  await writeWod(page, skill, o.writtenWodTitle);

  if (o.competitionWodTitle) {
    await writeWod(page, wod, o.competitionWodTitle);
    await wod.getByRole('checkbox', {name: t('SESSION.FORM.COMPETITION')}).check();
  } else if (o.borrowedWodTitle) {
    const borrowed = o.borrowedWodTitle;
    await wod.locator('.borrow-wod').click();
    // The dialog loads its default list when it opens and a late answer can replace a filtered one:
    // search again until the WOD shows up.
    const result = page.getByRole('button', {name: new RegExp(borrowed)});
    await expect(async () => {
      await page.locator('#borrow-search').fill('');
      await page.locator('#borrow-search').fill(borrowed);
      await expect(result).toBeVisible({timeout: 4_000});
    }).toPass({timeout: 30_000});
    await result.click();
    await expect(wod.getByText(borrowed)).toBeVisible();
  } else {
    throw new Error('createBoxSession needs borrowedWodTitle or competitionWodTitle');
  }

  await page.locator('#save-session').click();
  await expect(page).toHaveURL(/\/box\/sessions\/\d+$/);
  return Number(page.url().split('/').pop());
}

/** Id of the WOD of the "WOD" block, read from its leaderboard link on the session page. */
export async function wodIdOfWodBlock(page: Page): Promise<number> {
  const href = await page.getByRole('article', {name: t('SESSION.KINDS.WOD', {exact: true})})
    .locator('a[href^="/wods/"]').first().getAttribute('href');
  return Number(href!.split('/').pop());
}
