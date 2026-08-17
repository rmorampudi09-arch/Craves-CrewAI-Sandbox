import { NextRequest, NextResponse } from "next/server";
import { parseCart } from "@/lib/cart-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

function errorResponse(status: number) { return NextResponse.json({ error: status === 401 ? "SESSION_REQUIRED" : "CART_REQUEST_FAILED", message: status === 401 ? "Please sign in to use your cart." : "Cart request could not be completed." }, { status }); }
async function forward(request: NextRequest, method: "GET" | "DELETE") {
  if (method === "DELETE" && !isSameOrigin(request)) return NextResponse.json({ error: "ORIGIN_REJECTED", message: "Invalid cart request origin." }, { status: 403 });
  try { const upstream = await authenticatedApiFetch(request, "/cart", { method }); const body = await upstream.json().catch(() => null); if (!upstream.ok) return errorResponse(upstream.status); const cart = parseCart(body); return cart ? NextResponse.json(cart, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ error: "INVALID_UPSTREAM_RESPONSE", message: "Cart response validation failed." }, { status: 502 }); }
  catch (error) { return error instanceof SessionRequiredError ? errorResponse(401) : NextResponse.json({ error: "CART_UNAVAILABLE", message: "Your cart is unavailable right now." }, { status: 503 }); }
}
export async function GET(request: NextRequest) { return forward(request, "GET"); }
export async function DELETE(request: NextRequest) { return forward(request, "DELETE"); }
