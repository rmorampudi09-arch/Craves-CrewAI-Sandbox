import { NextRequest, NextResponse } from "next/server";
import { parseCart } from "@/lib/cart-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ error: "ORIGIN_REJECTED", message: "Invalid cart request origin." }, { status: 403 });
  try { const upstream = await authenticatedApiFetch(request, "/cart/validate", { method: "POST" }); const body = await upstream.json().catch(() => null); if (!upstream.ok) return NextResponse.json({ error: upstream.status === 401 ? "SESSION_REQUIRED" : "CART_VALIDATION_FAILED", message: upstream.status === 401 ? "Please sign in to use your cart." : "Cart validation failed. Review item availability." }, { status: upstream.status }); const cart = parseCart(body); return cart ? NextResponse.json(cart, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ error: "INVALID_UPSTREAM_RESPONSE", message: "Cart response validation failed." }, { status: 502 }); }
  catch (error) { return error instanceof SessionRequiredError ? NextResponse.json({ error: "SESSION_REQUIRED", message: "Please sign in to use your cart." }, { status: 401 }) : NextResponse.json({ error: "CART_UNAVAILABLE", message: "Cart validation is unavailable right now." }, { status: 503 }); }
}
