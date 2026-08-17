import { Edit2, MapPin } from "lucide-react";

interface AddressCardProps {
  addressLine: string;
  onEdit: () => void;
}

export function AddressCard({ addressLine, onEdit }: AddressCardProps) {
  return (
    <section
      aria-labelledby="profile-address-title"
      className="craves-surface p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex min-w-0 items-start gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-secondary text-contrast-red">
            <MapPin className="h-5 w-5" aria-hidden="true" />
          </div>
          <div className="min-w-0">
            <h2
              id="profile-address-title"
              className="font-display text-base font-semibold text-ink"
            >
              Default delivery address
            </h2>
            <p className="mt-1 text-sm leading-5 text-muted-foreground">
              {addressLine}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={onEdit}
          className="inline-flex min-h-11 shrink-0 items-center gap-2 rounded-lg border border-border bg-white px-3 text-sm font-semibold text-ink transition-colors hover:border-primary hover:bg-secondary"
          aria-label="Edit delivery addresses"
        >
          <Edit2 className="h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">Edit</span>
        </button>
      </div>
    </section>
  );
}

export default AddressCard;
