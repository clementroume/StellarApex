export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;

  // Allow for extension properties if needed
  [key: string]: any;
}
