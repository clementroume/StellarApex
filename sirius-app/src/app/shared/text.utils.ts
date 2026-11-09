/** Lower case without accents, so "épaulé" and "epaule" find the same names. */
export function normalize(text: string | null | undefined): string {
  return (text ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
