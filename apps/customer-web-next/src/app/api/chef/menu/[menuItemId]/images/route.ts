import { isSameOrigin } from "@/lib/request-security";
import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
const MAX_BYTES = 10_000_000;
function apiBaseUrl(): string { const value = process.env.CRAVES_API_BASE_URL?.trim(); if (!value?.startsWith("https://")) throw new Error("CRAVES_API_BASE_URL must use HTTPS"); return value.replace(/\/$/, ""); }

export async function POST(request: NextRequest, context: { params: Promise<{ menuItemId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { menuItemId } = await context.params;
  if (!UUID.test(menuItemId)) return NextResponse.json({ code: "INVALID_MENU_ITEM_ID" }, { status: 400 });
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
  const form = await request.formData().catch(() => null);
  const file = form?.get("file");
  const primary = form?.get("primary") === "true";
  if (!(file instanceof File) || !TYPES.has(file.type) || file.size < 1 || file.size > MAX_BYTES) return NextResponse.json({ code: "INVALID_MENU_IMAGE" }, { status: 400 });
  const upstreamForm = new FormData(); upstreamForm.set("file", file, file.name);
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 30_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/kitchens/me/menu-items/${encodeURIComponent(menuItemId)}/images?primary=${primary}`, { method: "POST", headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }, body: upstreamForm, cache: "no-store", signal: controller.signal });
    if (!upstream.ok) { const response = NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 404 ? "MENU_ITEM_NOT_FOUND" : "MENU_IMAGE_UPLOAD_FAILED" }, { status: upstream.status }); if (upstream.status === 401) response.cookies.delete("craves_access_token"); return response; }
    await upstream.json().catch(() => null);
    const response = NextResponse.json({ uploaded: true }); response.headers.set("Cache-Control", "no-store"); return response;
  } catch (error) { const timedOut = error instanceof Error && error.name === "AbortError"; return NextResponse.json({ code: timedOut ? "MENU_IMAGE_TIMEOUT" : "MENU_IMAGE_UNAVAILABLE" }, { status: timedOut ? 504 : 503 }); }
  finally { clearTimeout(timeout); }
}
