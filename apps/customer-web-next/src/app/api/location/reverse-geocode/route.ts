import { NextRequest, NextResponse } from "next/server";
import { isSameOrigin } from "@/lib/request-security";
import { reverseGeocodeWithAzureMaps } from "@/lib/server/azure-maps";

const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_REQUESTS = 30;
const rateBuckets = new Map<string, { startedAt: number; count: number }>();

function clientKey(request: NextRequest): string {
  const forwarded = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
  return forwarded || request.headers.get("x-azure-clientip")?.trim() || "unknown";
}

function allowRequest(key: string): boolean {
  const now = Date.now();
  const current = rateBuckets.get(key);
  if (!current || now - current.startedAt >= RATE_LIMIT_WINDOW_MS) {
    rateBuckets.set(key, { startedAt: now, count: 1 });
  } else if (current.count >= RATE_LIMIT_REQUESTS) {
    return false;
  } else {
    current.count += 1;
  }

  if (rateBuckets.size > 5_000) {
    for (const [candidate, bucket] of rateBuckets) {
      if (now - bucket.startedAt >= RATE_LIMIT_WINDOW_MS) rateBuckets.delete(candidate);
    }
  }
  return true;
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return NextResponse.json(
      { error: "ORIGIN_REJECTED", message: "Reverse geocoding is only available from Craves." },
      { status: 403 },
    );
  }

  if (!allowRequest(clientKey(request))) {
    return NextResponse.json(
      { error: "LOCATION_RATE_LIMITED", message: "Too many location lookups. Please try again shortly." },
      { status: 429, headers: { "Retry-After": "60" } },
    );
  }

  const body = (await request.json().catch(() => null)) as {
    latitude?: unknown;
    longitude?: unknown;
  } | null;
  const latitude = typeof body?.latitude === "number" ? body.latitude : Number.NaN;
  const longitude = typeof body?.longitude === "number" ? body.longitude : Number.NaN;

  if (
    !Number.isFinite(latitude)
    || latitude < -90
    || latitude > 90
    || !Number.isFinite(longitude)
    || longitude < -180
    || longitude > 180
  ) {
    return NextResponse.json(
      { error: "INVALID_LOCATION", message: "A valid current location is required." },
      { status: 400 },
    );
  }

  try {
    const address = await reverseGeocodeWithAzureMaps(latitude, longitude);
    return NextResponse.json(address, {
      headers: {
        "Cache-Control": "no-store, private",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch (error) {
    console.error("Azure Maps reverse geocoding failed", error);
    return NextResponse.json(
      {
        error: "REVERSE_GEOCODING_UNAVAILABLE",
        message: "Craves could not identify this address right now. Please try again.",
      },
      { status: 503 },
    );
  }
}
