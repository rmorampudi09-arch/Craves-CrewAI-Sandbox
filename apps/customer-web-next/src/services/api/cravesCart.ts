"use client";

import type { CustomerCart, ServerCartItem } from "@/lib/cart-contract";
import { getDish } from "./dishes";

export type CartItem = {
  id: string;
  menuItemId: string;
  name: string;
  chef: string;
  price: number;
  img: string;
  imageIsPlaceholder: boolean;
  qty: number;
  currency: string;
  lineTotal: number;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PLACEHOLDER_IMAGE = "/brand/craves-logo.svg";
let cart: CustomerCart | null = null;
let visualItems: CartItem[] = [];
const listeners = new Set<() => void>();

function mapItem(item: ServerCartItem): CartItem {
  const dish = getDish(item.menuItemId);
  return {
    id: item.id,
    menuItemId: item.menuItemId,
    name: item.itemName,
    chef: item.kitchenName,
    price: item.unitPrice,
    img: dish?.img ?? PLACEHOLDER_IMAGE,
    imageIsPlaceholder: !dish || dish.imageIsPlaceholder === true,
    qty: item.quantity,
    currency: item.currency,
    lineTotal: item.lineTotal,
  };
}

function notify() {
  for (const listener of listeners) listener();
}

function update(next: CustomerCart) {
  cart = next;
  visualItems = next.items.map(mapItem);
  notify();
}

function reset() {
  cart = null;
  visualItems = [];
  notify();
}

async function cartRequest(path: string, init?: RequestInit): Promise<CustomerCart> {
  const response = await fetch(path, {
    ...init,
    credentials: "same-origin",
    headers: {
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  const body = (await response.json().catch(() => null)) as
    | CustomerCart
    | { message?: string }
    | null;
  if (!response.ok || !body || !("items" in body)) {
    throw new Error(
      body && "message" in body && typeof body.message === "string"
        ? body.message
        : "Your cart could not be loaded from Craves.",
    );
  }
  update(body);
  return body;
}

export async function loadCart(): Promise<CartItem[]> {
  try {
    await cartRequest("/api/cart", { cache: "no-store" });
    return [...visualItems];
  } catch (error) {
    reset();
    throw error;
  }
}

export function getCart(): CartItem[] {
  return [...visualItems];
}

export function cartCount(): number {
  return visualItems.reduce((total, item) => total + item.qty, 0);
}

export function cartTotal(): number {
  return (
    cart?.foodSubtotal ??
    visualItems.reduce((total, item) => total + item.lineTotal, 0)
  );
}

export function cartCurrency(): string {
  return cart?.currency ?? visualItems[0]?.currency ?? "INR";
}

export async function addToCart(
  item: { id: string; name: string; chef: string; price: number; img: string },
  quantity = 1,
): Promise<void> {
  if (!UUID.test(item.id)) {
    throw new Error("This menu item is not valid for the Craves cart.");
  }
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 50) {
    throw new Error("Choose a quantity between 1 and 50.");
  }
  await cartRequest("/api/cart/items", {
    method: "POST",
    body: JSON.stringify({ menuItemId: item.id, quantity }),
  });
}

export async function setQty(id: string, quantity: number): Promise<void> {
  if (!UUID.test(id)) throw new Error("This cart item is invalid.");
  if (quantity <= 0) return removeFromCart(id);
  if (!Number.isInteger(quantity) || quantity > 50) {
    throw new Error("Choose a quantity between 1 and 50.");
  }
  await cartRequest(`/api/cart/items/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify({ quantity }),
  });
}

export async function removeFromCart(id: string): Promise<void> {
  if (!UUID.test(id)) throw new Error("This cart item is invalid.");
  await cartRequest(`/api/cart/items/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export async function clearCart(): Promise<void> {
  await cartRequest("/api/cart", { method: "DELETE" });
}

export async function validateCart(): Promise<CustomerCart> {
  return cartRequest("/api/cart/validate", { method: "POST" });
}

export function subscribeCart(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
