import { Link, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Heart, ArrowLeft, ShoppingCart, Trash2, Star } from "lucide-react";
import { loadSession } from "@/services/auth/cravesAuth";
import { addToCart } from "@/services/api/cravesCart";
import {
  getWishlist,
  removeFromWishlist,
  subscribeWishlist,
  type WishlistItem,
} from "@/services/api/cravesWishlist";
import { getDish } from "@/services/api/dishes";

// Route metadata (head tags, etc.) consumed by src/routes/wishlist.tsx
export const routeMeta = {
  head: () => ({
    meta: [{ title: "My Wishlist – Craves" }, { name: "robots", content: "noindex" }],
  }),
};

/**
 * Wishlist screen: everything saved via the heart button on a dish card or
 * the dish detail page (src/services/api/cravesWishlist.ts is the source of
 * truth) shows up here, with quick "Add to Cart" and remove actions.
 */
function WishlistPage() {
  const navigate = useNavigate();
  const [ready, setReady] = useState(false);
  const [items, setItems] = useState<WishlistItem[]>([]);

  useEffect(() => {
    void loadSession().then((session) => {
    if (!session) {
      navigate({ to: "/" });
      return;
    }
    setReady(true);
    const refresh = () => setItems(getWishlist());
    refresh();
    });
    return subscribeWishlist(() => setItems(getWishlist()));
  }, [navigate]);

  if (!ready) return null;

  return (
    <div className="min-h-screen bg-cream">
      <header className="border-b border-border bg-cream/90">
        <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-4">
          <Link to="/home" className="rounded-full p-2 hover:bg-black/5" aria-label="Back">
            <ArrowLeft className="h-5 w-5 text-ink" />
          </Link>
          <h1 className="font-display text-lg font-bold text-primary">My Wishlist</h1>
        </div>
      </header>

      {items.length === 0 ? (
        <main className="mx-auto max-w-3xl px-4 pt-10 text-center">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Heart className="h-8 w-8" />
          </div>
          <h2 className="mt-4 font-display text-xl font-bold text-ink">No favourites yet</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Tap the heart on any dish to save it here for quick reordering.
          </p>
          <Link to="/home" className="btn-primary mt-6 inline-flex rounded-lg px-6 py-2.5 text-sm">
            Browse dishes
          </Link>
        </main>
      ) : (
        <main className="mx-auto max-w-3xl px-4 py-6">
          <p className="mb-3 text-sm text-muted-foreground">
            {items.length} saved dish{items.length > 1 ? "es" : ""}
          </p>
          <ul className="space-y-3">
            {items.map((item) => {
              const dish = getDish(item.id);
              return (
                <li
                  key={item.id}
                  className="flex items-center gap-3 rounded-2xl border border-border bg-card p-3 shadow-sm"
                >
                  <Link to="/dish/$id" params={{ id: item.id }} className="shrink-0">
                    <img
                      src={item.img}
                      alt={item.name}
                      width={72}
                      height={72}
                      className="h-[72px] w-[72px] rounded-xl object-cover"
                    />
                  </Link>
                  <div className="min-w-0 flex-1">
                    <Link to="/dish/$id" params={{ id: item.id }}>
                      <h3 className="truncate font-display text-base font-bold text-ink">
                        {item.name}
                      </h3>
                    </Link>
                    <p className="text-xs text-muted-foreground">by {item.chef}</p>
                    <div className="mt-1 flex items-center gap-2">
                      <span className="font-display text-sm font-bold text-ink">₹{item.price}</span>
                      {dish && (
                        <span className="flex items-center gap-0.5 text-xs text-muted-foreground">
                          <Star className="h-3 w-3 fill-primary text-primary" /> {dish.rating}
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-2">
                    <button
                      type="button"
                      onClick={() => removeFromWishlist(item.id)}
                      className="text-muted-foreground hover:text-destructive"
                      aria-label="Remove from wishlist"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => void addToCart(item, 1)}
                      className="flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-xs font-bold text-primary-foreground"
                    >
                      <ShoppingCart className="h-3.5 w-3.5" /> Add
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        </main>
      )}
    </div>
  );
}

export default WishlistPage;
