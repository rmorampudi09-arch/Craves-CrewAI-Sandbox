import { cookies } from "next/headers";
import { NextRequest } from "next/server";

export class SessionRequiredError extends Error {}

export function apiBaseUrl(): string {
  const value = process.env.CRAVES_API_BASE_URL?.trim();
  if (!value?.startsWith("https://")) throw new Error("CRAVES_API_BASE_URL must use HTTPS");
  return value.replace(/\/$/, "");
}

function sanitizeApiPath(path: string): string {
  if (!path.startsWith("/") || path.startsWith("//") || path.includes("..") || /[\r\n]/.test(path)) {
    throw new Error("Invalid API path");
  }
  return path;
}

export async function authenticatedApiFetch(
  request: NextRequest,
  path: string,
  init: RequestInit = {},
  timeoutMs = 10_000,
): Promise<Response> {
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token) throw new SessionRequiredError("Customer session is required");
  return authenticatedApiFetchWithToken(token, path, init, timeoutMs);
}

export async function authenticatedApiFetchFromServer(
  path: string,
  init: RequestInit = {},
  timeoutMs = 10_000,
): Promise<Response> {
  const token = (await cookies()).get("craves_access_token")?.value;
  if (!token) throw new SessionRequiredError("Customer session is required");
  return authenticatedApiFetchWithToken(token, path, init, timeoutMs);
}

async function authenticatedApiFetchWithToken(
  token: string,
  path: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const safePath = sanitizeApiPath(path);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(`${apiBaseUrl()}${safePath}`, {
      ...init,
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
        ...init.headers,
      },
      cache: "no-store",
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timeout);
  }
}

export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
