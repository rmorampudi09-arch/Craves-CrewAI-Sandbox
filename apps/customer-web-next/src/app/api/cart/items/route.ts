import { NextRequest, NextResponse } from "next/server";
import { parseAddItemInput, parseCart } from "@/lib/cart-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ error: "ORIGIN_REJECTED", message: "Invalid cart request origin." }, { status: 403 });
  const input = parseAddItemInput(await request.json().catch(() => null)); if (!input) return NextResponse.json({ error: "INVALID_CART_ITEM", message: "A valid menu item and quantity are required." }, { status: 400 });
  try { const upstream = await authenticatedApiFetch(request, "/cart/items", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }); const body = await upstream.json().catch(() => null); if (!upstream.ok) return NextResponse.json({ error: upstream.status === 401 ? "SESSION_REQUIRED" : "CART_UPDATE_FAILED", message: upstream.status === 401 ? "Please sign in to use your cart." : "The item could not be added to your cart." }, { status: upstream.status }); const cart = parseCart(body); return cart ? NextResponse.json(cart, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ error: "INVALID_UPSTREAM_RESPONSE", message: "Cart response validation failed." }, { status: 502 }); }
  catch (error) { return error instanceof SessionRequiredError ? NextResponse.json({ error: "SESSION_REQUIRED", message: "Please sign in to use your cart." }, { status: 401 }) : NextResponse.json({ error: "CART_UNAVAILABLE", message: "The item could not be added right now." }, { status: 503 }); }
}
