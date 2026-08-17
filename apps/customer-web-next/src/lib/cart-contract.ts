export type ServerCartItem = { id: string; menuItemId: string; kitchenId: string; itemName: string; kitchenName: string; unitPrice: number; currency: string; quantity: number; lineTotal: number; createdAt: string; updatedAt: string };
export type CustomerCart = { id: string; currency: string; items: ServerCartItem[]; foodSubtotal: number };
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function record(value: unknown): Record<string, unknown> | null { return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null; }
function text(value: unknown, max: number): string | null { if (typeof value !== "string") return null; const result = value.trim(); return result && result.length <= max ? result : null; }
function money(value: unknown): number | null { const result = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN; return Number.isFinite(result) && result >= 0 && result <= 10_000_000 ? result : null; }
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }
function parseItem(value: unknown): ServerCartItem | null {
  const raw = record(value); if (!raw) return null;
  const id = text(raw.id, 64); const menuItemId = text(raw.menuItemId, 64); const kitchenId = text(raw.kitchenId, 64); const itemName = text(raw.itemName, 180); const kitchenName = text(raw.kitchenName, 180); const currency = text(raw.currency, 3); const unitPrice = money(raw.unitPrice); const lineTotal = money(raw.lineTotal); const quantity = typeof raw.quantity === "number" && Number.isInteger(raw.quantity) && raw.quantity >= 1 && raw.quantity <= 100 ? raw.quantity : null; const createdAt = instant(raw.createdAt); const updatedAt = instant(raw.updatedAt);
  if (!id || !UUID.test(id) || !menuItemId || !UUID.test(menuItemId) || !kitchenId || !UUID.test(kitchenId) || !itemName || !kitchenName || !currency || unitPrice === null || lineTotal === null || quantity === null || !createdAt || !updatedAt) return null;
  return { id, menuItemId, kitchenId, itemName, kitchenName, unitPrice, currency: currency.toUpperCase(), quantity, lineTotal, createdAt, updatedAt };
}
export function parseCart(value: unknown): CustomerCart | null {
  const raw = record(value); if (!raw || !Array.isArray(raw.items) || raw.items.length > 200) return null;
  const id = text(raw.id, 64); const currency = text(raw.currency, 3); const totals = record(raw.totals); const foodSubtotal = totals ? money(totals.foodSubtotal) : null; const totalCurrency = totals ? text(totals.currency, 3) : null; const items = raw.items.map(parseItem);
  return id && UUID.test(id) && currency && totalCurrency && currency.toUpperCase() === totalCurrency.toUpperCase() && foodSubtotal !== null && !items.some((item) => item === null) ? { id, currency: currency.toUpperCase(), items: items as ServerCartItem[], foodSubtotal } : null;
}
export function parseAddItemInput(value: unknown): { menuItemId: string; quantity: number } | null { const raw = record(value); const menuItemId = raw ? text(raw.menuItemId, 64) : null; const quantity = raw && typeof raw.quantity === "number" && Number.isInteger(raw.quantity) && raw.quantity >= 1 && raw.quantity <= 100 ? raw.quantity : null; return menuItemId && UUID.test(menuItemId) && quantity !== null ? { menuItemId, quantity } : null; }
export function parseQuantityInput(value: unknown): { quantity: number } | null { const raw = record(value); const quantity = raw && typeof raw.quantity === "number" && Number.isInteger(raw.quantity) && raw.quantity >= 1 && raw.quantity <= 100 ? raw.quantity : null; return quantity === null ? null : { quantity }; }
