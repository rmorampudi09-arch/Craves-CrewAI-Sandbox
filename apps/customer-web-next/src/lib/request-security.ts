import type { NextRequest } from "next/server";

type OriginCheckInput = {
  origin: string | null;
  requestUrl: string;
  forwardedProto?: string | null;
  forwardedHost?: string | null;
  host?: string | null;
};

function lastForwardedValue(value: string | null | undefined): string | null {
  if (!value) return null;
  const values = value.split(",").map((part) => part.trim()).filter(Boolean);
  return values.at(-1) ?? null;
}

function parseOrigin(value: string): string | null {
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

function forwardedOrigin(
  proto: string | null | undefined,
  host: string | null | undefined,
): string | null {
  const publicProto = lastForwardedValue(proto)?.toLowerCase();
  const publicHost = lastForwardedValue(host);
  if ((publicProto !== "https" && publicProto !== "http") || !publicHost) return null;
  return parseOrigin(`${publicProto}://${publicHost}`);
}

export function isRequestOriginAllowed(input: OriginCheckInput): boolean {
  if (!input.origin) return false;
  const suppliedOrigin = parseOrigin(input.origin);
  if (!suppliedOrigin) return false;

  try {
    if (suppliedOrigin === new URL(input.requestUrl).origin) return true;
  } catch {
    return false;
  }

  const publicHost = input.forwardedHost || input.host;
  return suppliedOrigin === forwardedOrigin(input.forwardedProto, publicHost);
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
