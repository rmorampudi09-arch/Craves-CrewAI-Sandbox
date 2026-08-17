import { NextRequest, NextResponse } from "next/server";
import { parseCart, parseQuantityInput } from "@/lib/cart-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

async function itemId(context: { params: Promise<{ cartItemId: string }> }) { const { cartItemId } = await context.params; return isUuid(cartItemId) ? cartItemId : null; }
function failure(status: number) { return NextResponse.json({ error: status === 401 ? "SESSION_REQUIRED" : status === 404 ? "CART_ITEM_NOT_FOUND" : "CART_UPDATE_FAILED", message: status === 401 ? "Please sign in to use your cart." : status === 404 ? "Cart item was not found." : "Cart could not be updated." }, { status }); }
async function responseCart(upstream: Response) { const body = await upstream.json().catch(() => null); if (!upstream.ok) return failure(upstream.status); const cart = parseCart(body); return cart ? NextResponse.json(cart, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ error: "INVALID_UPSTREAM_RESPONSE", message: "Cart response validation failed." }, { status: 502 }); }
export async function PUT(request: NextRequest, context: { params: Promise<{ cartItemId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ error: "ORIGIN_REJECTED", message: "Invalid cart request origin." }, { status: 403 }); const id = await itemId(context); if (!id) return NextResponse.json({ error: "INVALID_CART_ITEM_ID", message: "Cart item id is invalid." }, { status: 400 }); const input = parseQuantityInput(await request.json().catch(() => null)); if (!input) return NextResponse.json({ error: "INVALID_QUANTITY", message: "Quantity must be between 1 and 100." }, { status: 400 });
  try { return responseCart(await authenticatedApiFetch(request, `/cart/items/${id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) })); } catch (error) { return error instanceof SessionRequiredError ? failure(401) : failure(503); }
}
export async function DELETE(request: NextRequest, context: { params: Promise<{ cartItemId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ error: "ORIGIN_REJECTED", message: "Invalid cart request origin." }, { status: 403 }); const id = await itemId(context); if (!id) return NextResponse.json({ error: "INVALID_CART_ITEM_ID", message: "Cart item id is invalid." }, { status: 400 });
  try { return responseCart(await authenticatedApiFetch(request, `/cart/items/${id}`, { method: "DELETE" })); } catch (error) { return error instanceof SessionRequiredError ? failure(401) : failure(503); }
}
