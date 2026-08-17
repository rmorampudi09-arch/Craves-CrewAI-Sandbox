import { NextRequest, NextResponse } from "next/server";
import { parseKitchenDiscovery } from "@/lib/discovery-contract";
import { publicApiFetch } from "@/lib/public-api";

export async function GET(request: NextRequest) {
  const latitude = Number(request.nextUrl.searchParams.get("latitude")); const longitude = Number(request.nextUrl.searchParams.get("longitude")); const radiusMeters = Number(request.nextUrl.searchParams.get("radiusMeters") ?? "5000"); const page = Number(request.nextUrl.searchParams.get("page") ?? "0"); const size = Number(request.nextUrl.searchParams.get("size") ?? "20");
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180 || !Number.isInteger(radiusMeters) || radiusMeters < 1 || radiusMeters > 100_000 || !Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 50) return NextResponse.json({ error: "INVALID_LOCATION", message: "Valid latitude, longitude and discovery bounds are required." }, { status: 400 });
  const query = new URLSearchParams({ latitude: String(latitude), longitude: String(longitude), radiusMeters: String(radiusMeters), page: String(page), size: String(size) });
  try {
    const upstream = await publicApiFetch(`/discovery/kitchens?${query}`); const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ error: "DISCOVERY_UNAVAILABLE", message: "Nearby kitchens are unavailable right now." }, { status: upstream.status });
    const parsed = parseKitchenDiscovery(body); return parsed ? NextResponse.json(parsed, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ error: "INVALID_UPSTREAM_RESPONSE", message: "Catalog response validation failed." }, { status: 502 });
  } catch (error) {
    const timeout = error instanceof Error && error.name === "AbortError";
    return NextResponse.json({ error: timeout ? "DISCOVERY_TIMEOUT" : "DISCOVERY_UNAVAILABLE", message: "Nearby kitchens are unavailable right now." }, { status: timeout ? 504 : 502 });
  }
}
