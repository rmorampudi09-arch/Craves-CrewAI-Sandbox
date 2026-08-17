import { CartItemRow } from "@/components/cart/CartItemRow";
import type { CartItem } from "@/services/api/cravesCart";

interface CartItemListProps {
  items: CartItem[];
  busyItemId: string | null;
  onRemove: (id: string) => void;
  onSetQty: (id: string, qty: number) => void;
}

export function CartItemList({
  items,
  busyItemId,
  onRemove,
  onSetQty,
}: CartItemListProps) {
  return (
    <div className="space-y-4">
      {items.map((item) => (
        <CartItemRow
          key={item.id}
          item={item}
          disabled={busyItemId === item.id}
          onRemove={() => onRemove(item.id)}
          onDecrease={() => onSetQty(item.id, item.qty - 1)}
          onIncrease={() => onSetQty(item.id, item.qty + 1)}
        />
      ))}
    </div>
  );
}

export default CartItemList;
