export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;

  // Allow for extension properties if needed
  [key: string]: any;
}

/** Localized `detail` of a backend ProblemDetail error, if any (show it instead of a generic message). */
export function problemDetailOf(error: unknown): string | null {
  const body = (error as {error?: Partial<ProblemDetail>} | null)?.error;
  return typeof body?.detail === 'string' && body.detail ? body.detail : null;
}
