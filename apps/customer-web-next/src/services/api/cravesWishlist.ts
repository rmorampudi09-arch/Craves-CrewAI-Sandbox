export type WishlistItem = {
  id: string;
  name: string;
  chef: string;
  price: number;
  img: string;
};

const KEY = "craves.wishlist";
const EVT = "craves:wishlist-change";

function read(): WishlistItem[] {
  if (typeof window === "undefined") return [];
  try {
    return JSON.parse(localStorage.getItem(KEY) || "[]") as WishlistItem[];
  } catch {
    return [];
  }
}

function write(items: WishlistItem[]) {
  localStorage.setItem(KEY, JSON.stringify(items));
  window.dispatchEvent(new Event(EVT));
}

export function getWishlist(): WishlistItem[] {
  return read();
}

export function wishlistCount(): number {
  return read().length;
}

export function isWishlisted(id: string): boolean {
  return read().some((x) => x.id === id);
}

/** Adds the item if it's not already saved, removes it if it is. Returns the new state. */
export function toggleWishlist(item: WishlistItem): boolean {
  const items = read();
  const i = items.findIndex((x) => x.id === item.id);
  if (i >= 0) {
    items.splice(i, 1);
    write(items);
    return false;
  }
  items.push(item);
  write(items);
  return true;
}

export function removeFromWishlist(id: string) {
  write(read().filter((x) => x.id !== id));
}

export function subscribeWishlist(fn: () => void) {
  const handler = () => fn();
  window.addEventListener(EVT, handler);
  window.addEventListener("storage", handler);
  return () => {
    window.removeEventListener(EVT, handler);
    window.removeEventListener("storage", handler);
  };
}
