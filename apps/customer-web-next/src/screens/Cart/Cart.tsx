import { useNavigate } from "@tanstack/react-router";
import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { loadSession } from "@/services/auth/cravesAuth";
import {
  cartCurrency,
  cartTotal,
  getCart,
  loadCart,
  removeFromCart,
  setQty,
  subscribeCart,
  validateCart,
  type CartItem,
} from "@/services/api/cravesCart";
import { CartHeader } from "@/components/cart/CartHeader";
import { EmptyCartState } from "@/components/cart/EmptyCartState";
import { CartItemList } from "@/components/cart/CartItemList";
import { BillSummaryCard } from "@/components/cart/BillSummaryCard";
import { CartCheckoutBar } from "@/components/cart/CartCheckoutBar";

export const routeMeta = {
  head: () => ({
    meta: [
      { title: "Your Cart – Craves" },
      { name: "robots", content: "noindex" },
    ],
  }),
};

function CartSkeleton() {
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]" aria-hidden="true">
      <div className="space-y-4">
        {Array.from({ length: 2 }, (_, index) => (
          <div key={index} className="h-40 animate-pulse rounded-2xl bg-grey-200" />
        ))}
      </div>
      <div className="h-52 animate-pulse rounded-2xl bg-grey-200" />
    </div>
  );
}

function CartPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [busyItemId, setBusyItemId] = useState<string | null>(null);
  const [validating, setValidating] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setMessage("");
    try {
      await loadCart();
      setItems(getCart());
    } catch (error) {
      setItems([]);
      setMessage(
        error instanceof Error
          ? error.message
          : "Your cart could not be loaded from Craves.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    void loadSession().then((session) => {
      if (!active) return;
      if (!session) {
        navigate({ to: "/" });
        return;
      }
      void refresh();
    });
    const unsubscribe = subscribeCart(() => setItems(getCart()));
    return () => {
      active = false;
      unsubscribe();
    };
  }, [navigate, refresh]);

  async function changeQuantity(id: string, quantity: number) {
    setBusyItemId(id);
    setMessage("");
    try {
      await setQty(id, quantity);
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "The cart quantity could not be updated.",
      );
    } finally {
      setBusyItemId(null);
    }
  }

  async function removeItem(id: string) {
    setBusyItemId(id);
    setMessage("");
    try {
      await removeFromCart(id);
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "The cart item could not be removed.",
      );
    } finally {
      setBusyItemId(null);
    }
  }

  async function continueToAddress() {
    setValidating(true);
    setMessage("");
    try {
      await validateCart();
      navigate({ to: "/payment" });
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Your cart could not be validated. Review its items and try again.",
      );
    } finally {
      setValidating(false);
    }
  }

  const subtotal = cartTotal();
  const currency = cartCurrency();

  return (
    <div className="min-h-screen bg-white pb-32 text-ink">
      <CartHeader onBack={() => navigate({ to: "/home" })} />
      <main className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
        <div className="mb-6">
          <p className="craves-overline text-primary">Review before checkout</p>
          <h1 className="mt-1 font-display text-3xl font-bold tracking-[-0.04em] text-ink">
            Your selected dishes
          </h1>
          {!loading && items.length > 0 && (
            <p className="mt-2 text-sm text-muted-foreground">
              {items.reduce((total, item) => total + item.qty, 0)} total items from the backend cart.
            </p>
          )}
        </div>

        {loading ? (
          <>
            <CartSkeleton />
            <p className="sr-only" role="status">Loading your Craves cart</p>
          </>
        ) : message && items.length === 0 ? (
          <div className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
            <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
            <h2 className="mt-4 font-display text-xl font-bold text-ink">Cart unavailable</h2>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">{message}</p>
            <button type="button" onClick={() => void refresh()} className="btn-primary mt-6">
              <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
            </button>
          </div>
        ) : items.length === 0 ? (
          <EmptyCartState onBrowseMenu={() => navigate({ to: "/home" })} />
        ) : (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
            <CartItemList
              items={items}
              busyItemId={busyItemId}
              onRemove={(id) => void removeItem(id)}
              onSetQty={(id, quantity) => void changeQuantity(id, quantity)}
            />
            <div className="lg:sticky lg:top-24">
              <BillSummaryCard subtotal={subtotal} currency={currency} />
            </div>
          </div>
        )}

        {message && items.length > 0 && (
          <p role="alert" className="mt-5 rounded-xl border border-error/20 bg-white p-3 text-sm font-medium text-error">
            {message}
          </p>
        )}
      </main>
      {!loading && items.length > 0 && (
        <CartCheckoutBar
          total={subtotal}
          currency={currency}
          disabled={validating || busyItemId !== null}
          onContinue={() => void continueToAddress()}
        />
      )}
    </div>
  );
}

export default CartPage;
