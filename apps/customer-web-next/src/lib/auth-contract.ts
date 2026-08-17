export type CravesIdentity = {
  id: string;
  phoneNumber: string;
  email: string | null;
  emailVerified: boolean;
  displayName: string | null;
  status: string;
  roles: string[];
};

export type CravesSessionExchange = {
  accessToken: string;
  expiresIn: number;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  identity: CravesIdentity;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function text(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= maxLength ? result : null;
}

export function parseIdentity(value: unknown): CravesIdentity | null {
  if (!value || typeof value !== "object") return null;
  const identity = value as Record<string, unknown>;
  const id = text(identity.id, 64);
  const phoneNumber = text(identity.phoneNumber, 24);
  const status = text(identity.status, 40);
  const roles = Array.isArray(identity.roles)
    ? identity.roles.filter((role): role is string => typeof role === "string").map((role) => role.trim()).filter(Boolean).slice(0, 10)
    : [];
  if (!id || !UUID.test(id) || !phoneNumber || !status || roles.length === 0) return null;
  return {
    id,
    phoneNumber,
    email: text(identity.email, 320),
    emailVerified: identity.emailVerified === true,
    displayName: text(identity.displayName, 160),
    status,
    roles,
  };
}

export function parseSessionExchange(value: unknown): CravesSessionExchange | null {
  if (!value || typeof value !== "object") return null;
  const body = value as Record<string, unknown>;
  const accessToken = text(body.accessToken, 20_000);
  const refreshToken = text(body.refreshToken, 20_000);
  const refreshTokenExpiresAt = text(body.refreshTokenExpiresAt, 80);
  const expiresIn = typeof body.expiresIn === "number" && Number.isFinite(body.expiresIn)
    ? Math.floor(body.expiresIn)
    : 0;
  const identity = parseIdentity(body.identity);
  if (!accessToken || !refreshToken || !refreshTokenExpiresAt || Number.isNaN(Date.parse(refreshTokenExpiresAt)) || expiresIn < 60 || !identity) return null;
  return {
    accessToken,
    expiresIn: Math.min(expiresIn, 60 * 60),
    refreshToken,
    refreshTokenExpiresAt,
    identity,
  };
}

export function safeReturnPath(value: unknown): string {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) return "/";
  if (value.length > 500 || /[\r\n]/.test(value)) return "/";
  return value;
}

export function publicAuthError(status: number): string {
  if (status === 400) return "The sign-in request was invalid. Please try again.";
  if (status === 401 || status === 403) return "The OTP session could not be verified. Please request a new OTP.";
  if (status === 429) return "Too many attempts. Please wait before requesting another OTP.";
  return "Sign-in is temporarily unavailable. Please try again.";
}
