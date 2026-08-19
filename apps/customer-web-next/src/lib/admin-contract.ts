export type AdminIdentity = {
  displayName: string | null;
  email: string | null;
  status: string;
  adminEnabled: boolean;
  roles: string[];
};

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

export function parseAdminIdentity(value: unknown): AdminIdentity | null {
  const envelope = record(value);
  if (!envelope) return null;

  // Auth Service returns GET /auth/me as { identity: { ... } }.
  // Retain flat-response compatibility for older deployed revisions.
  const raw = record(envelope.identity) ?? envelope;
  const status = text(raw.status, 40)?.toUpperCase() ?? null;
  const roles = Array.isArray(raw.roles)
    ? raw.roles
        .filter((role): role is string => typeof role === "string")
        .map((role) => role.trim().toUpperCase())
        .filter(Boolean)
        .slice(0, 20)
    : [];

  if (!status || roles.length === 0) return null;

  return {
    displayName: text(raw.displayName, 160),
    email: text(raw.email, 320),
    status,
    roles,
    adminEnabled:
      status === "ACTIVE" &&
      roles.some((role) =>
        ["ADMIN", "INTERNAL_ADMIN", "SUPER_ADMIN", "SUPPORT_ADMIN"].includes(role),
      ),
  };
}
