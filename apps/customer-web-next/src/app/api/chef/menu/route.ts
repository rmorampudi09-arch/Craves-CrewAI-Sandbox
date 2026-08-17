import { isSameOrigin } from "@/lib/request-security";
import { NextRequest, NextResponse } from "next/server";
import { parseChefMenuItem, parseChefMenuItemInput, parseChefMenuItems } from "@/lib/chef-menu-contract";

export const dynamic = "force-dynamic";
function apiBaseUrl(): string { const value = process.env.CRAVES_API_BASE_URL?.trim(); if (!value?.startsWith("https://")) throw new Error("CRAVES_API_BASE_URL must use HTTPS"); return value.replace(/\/$/, ""); }

async function requestUpstream(request: NextRequest, method: "GET" | "POST", body?: unknown) {
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 10_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/kitchens/me/menu-items`, { method, headers: { Authorization: `Bearer ${token}`, Accept: "application/json", ...(body === undefined ? {} : { "Content-Type": "application/json" }) }, body: body === undefined ? undefined : JSON.stringify(body), cache: "no-store", signal: controller.signal });
    if (!upstream.ok) { const response = NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "CHEF_ACCESS_REQUIRED" : "MENU_REQUEST_FAILED" }, { status: upstream.status }); if (upstream.status === 401) response.cookies.delete("craves_access_token"); return response; }
    const raw = await upstream.json().catch(() => null);
    const parsed = method === "GET" ? parseChefMenuItems(raw) : parseChefMenuItem(raw);
    if (!parsed) return NextResponse.json({ code: "INVALID_MENU_RESPONSE" }, { status: 502 });
    const response = NextResponse.json(parsed); response.headers.set("Cache-Control", "no-store"); return response;
  } catch (error) { const timedOut = error instanceof Error && error.name === "AbortError"; return NextResponse.json({ code: timedOut ? "MENU_TIMEOUT" : "MENU_UNAVAILABLE" }, { status: timedOut ? 504 : 503 }); }
  finally { clearTimeout(timeout); }
}

export async function GET(request: NextRequest) { return requestUpstream(request, "GET"); }
export async function POST(request: NextRequest) { if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 }); const input = parseChefMenuItemInput(await request.json().catch(() => null)); if (!input) return NextResponse.json({ code: "INVALID_MENU_ITEM" }, { status: 400 }); return requestUpstream(request, "POST", input); }
