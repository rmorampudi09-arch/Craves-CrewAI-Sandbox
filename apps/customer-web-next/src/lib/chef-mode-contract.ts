export type ChefModeIdentity = {
  id: string;
  phoneNumber: string;
  displayName: string | null;
  status: string;
  roles: string[];
  chefEnabled: boolean;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function text(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized && normalized.length <= maxLength ? normalized : null;
}

export function parseChefModeIdentity(value: unknown): ChefModeIdentity | null {
  if (!value || typeof value !== "object") return null;
  const identity = value as Record<string, unknown>;
  const id = text(identity.id, 64);
  const phoneNumber = text(identity.phoneNumber, 24);
  const displayName = text(identity.displayName, 160);
  const status = text(identity.status, 40);
  const roles = Array.isArray(identity.roles)
    ? identity.roles
        .filter((role): role is string => typeof role === "string")
        .map(role => role.trim().toUpperCase())
        .filter(Boolean)
        .slice(0, 10)
    : [];
  if (!id || !UUID.test(id) || !phoneNumber || !status || roles.length === 0) return null;
  return { id, phoneNumber, displayName, status, roles, chefEnabled: roles.includes("CHEF") };
}
