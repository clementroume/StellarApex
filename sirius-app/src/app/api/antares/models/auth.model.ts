export interface AuthenticationRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmationPassword: string;
}

export interface TokenRefreshResponse {
  accessToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

/** New password chosen with the token of a mailed reset link. */
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmationPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}
