import { isSameOrigin } from "@/lib/request-security";
import { NextRequest, NextResponse } from "next/server";
import { parseChefMenuItem, parseChefMenuItemInput } from "@/lib/chef-menu-contract";

export const dynamic = "force-dynamic";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function apiBaseUrl(): string { const value = process.env.CRAVES_API_BASE_URL?.trim(); if (!value?.startsWith("https://")) throw new Error("CRAVES_API_BASE_URL must use HTTPS"); return value.replace(/\/$/, ""); }

export async function PUT(request: NextRequest, context: { params: Promise<{ menuItemId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { menuItemId } = await context.params;
  if (!UUID.test(menuItemId)) return NextResponse.json({ code: "INVALID_MENU_ITEM_ID" }, { status: 400 });
  const input = parseChefMenuItemInput(await request.json().catch(() => null));
  if (!input) return NextResponse.json({ code: "INVALID_MENU_ITEM" }, { status: 400 });
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 10_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/kitchens/me/menu-items/${encodeURIComponent(menuItemId)}`, { method: "PUT", headers: { Authorization: `Bearer ${token}`, Accept: "application/json", "Content-Type": "application/json" }, body: JSON.stringify(input), cache: "no-store", signal: controller.signal });
    if (!upstream.ok) { const response = NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 404 ? "MENU_ITEM_NOT_FOUND" : "MENU_ITEM_UPDATE_FAILED" }, { status: upstream.status }); if (upstream.status === 401) response.cookies.delete("craves_access_token"); return response; }
    const item = parseChefMenuItem(await upstream.json().catch(() => null));
    if (!item) return NextResponse.json({ code: "INVALID_MENU_RESPONSE" }, { status: 502 });
    const response = NextResponse.json(item); response.headers.set("Cache-Control", "no-store"); return response;
  } catch (error) { const timedOut = error instanceof Error && error.name === "AbortError"; return NextResponse.json({ code: timedOut ? "MENU_TIMEOUT" : "MENU_UNAVAILABLE" }, { status: timedOut ? 504 : 503 }); }
  finally { clearTimeout(timeout); }
}
