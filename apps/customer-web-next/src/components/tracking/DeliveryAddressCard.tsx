import { MapPin, Phone } from "lucide-react";

/** "Delivering to" card: saved address plus a rider-will-call note. */
export function DeliveryAddressCard({ address }: { address?: string }) {
  return (
    <section className="mt-5 rounded-2xl border border-border bg-card p-5">
      <h3 className="font-display text-lg font-bold text-ink">Delivering to</h3>
      <p className="mt-2 flex items-start gap-2 text-sm text-ink/80">
        <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        {address || "Your saved address"}
      </p>
      <p className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
        <Phone className="h-3.5 w-3.5" /> Rider will call before arriving.
      </p>
    </section>
  );
}

export default DeliveryAddressCard;
