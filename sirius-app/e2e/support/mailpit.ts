import {expect} from '@playwright/test';

/** Local Mailpit (docker service `deneb-mail`). Local stack only. */
const MAILPIT = 'http://localhost:8025/api/v1';

/** Waits for the first mail sent to `to` and returns its plain text. */
export async function waitForMail(to: string): Promise<string> {
  let text = '';
  await expect.poll(async () => {
    const search = await fetch(`${MAILPIT}/search?query=${encodeURIComponent(`to:${to}`)}`);
    const {messages} = (await search.json()) as {messages: {ID: string}[]};
    if (!messages?.length) return false;
    const mail = await fetch(`${MAILPIT}/message/${messages[0].ID}`);
    text = ((await mail.json()) as {Text: string}).Text;
    return true;
  }, {message: `no mail for ${to} in Mailpit`, timeout: 20_000}).toBe(true);
  return text;
}

/** The first link of the mail that points at a Sirius page, as a path + fragment. */
export function sirius(link: string): string {
  const url = new URL(link);
  return url.pathname + url.search + url.hash;
}

export function firstLink(text: string, containing: string): string {
  const match = text.match(new RegExp(`https?://[^\\s<>"')\\]]*${containing}[^\\s<>"')\\]]*`));
  if (!match) throw new Error(`No link containing "${containing}" in the mail`);
  return match[0];
}
