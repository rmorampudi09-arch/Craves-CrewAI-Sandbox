import type { NextRequest } from "next/server";

type OriginCheckInput = {
  origin: string | null;
  requestUrl: string;
  forwardedProto?: string | null;
  forwardedHost?: string | null;
  host?: string | null;
};

function getLastForwardedValue(value: string | null | undefined): string | null {
  if (!value) return null;
  const values = value
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean);
  return values.at(-1) ?? null;
}

function normalizeOrigin(value: string): string | null {
  try {
    const url = new URL(value);
    if (
      (url.protocol !== "https:" && url.protocol !== "http:") ||
      url.username ||
      url.password ||
      url.pathname !== "/" ||
      url.search ||
      url.hash
    ) {
      return null;
    }
    return url.origin;
  } catch {
    return null;
  }
}

function resolveForwardedOrigin(
  forwardedProto: string | null | undefined,
  forwardedHost: string | null | undefined,
): string | null {
  const proto = getLastForwardedValue(forwardedProto)?.toLowerCase();
  const host = getLastForwardedValue(forwardedHost);

  if ((proto !== "https" && proto !== "http") || !host) {
    return null;
  }

  return normalizeOrigin(`${proto}://${host}`);
}

export function isRequestOriginAllowed(input: OriginCheckInput): boolean {
  if (!input.origin) {
    return false;
  }

  const suppliedOrigin = normalizeOrigin(input.origin);
  if (!suppliedOrigin) {
    return false;
  }

  try {
    const requestOrigin = new URL(input.requestUrl).origin;
    if (suppliedOrigin === requestOrigin) {
      return true;
    }
  } catch {
    return false;
  }

  const publicHost = input.forwardedHost ?? input.host;
  return suppliedOrigin === resolveForwardedOrigin(input.forwardedProto, publicHost);
}

export function isSameOrigin(request: NextRequest): boolean {
  return isRequestOriginAllowed({
    origin: request.headers.get("origin"),
    requestUrl: request.url,
    forwardedProto: request.headers.get("x-forwarded-proto"),
    forwardedHost: request.headers.get("x-forwarded-host"),
    host: request.headers.get("host"),
  });
}
