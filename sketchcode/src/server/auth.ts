import * as crypto from 'crypto';

let sessionToken: string | null = null;

/** Generate a new session token */
export function generateToken(): string {
  sessionToken = crypto.randomBytes(32).toString('hex');
  return sessionToken;
}

/** Validate a token against the current session */
export function validateToken(token: string): boolean {
  return sessionToken !== null && token === sessionToken;
}

/** Clear the session token */
export function clearToken(): void {
  sessionToken = null;
}

/** Get the current token (for QR code generation) */
export function getCurrentToken(): string | null {
  return sessionToken;
}
