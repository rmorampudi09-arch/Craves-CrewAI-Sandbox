import { Mail, Pencil, Phone, ShieldCheck } from "lucide-react";
import type { CustomerProfile } from "@/lib/profile-contract";
import type { CravesUser } from "@/services/auth/cravesAuth";

interface AccountCardProps {
  user: CravesUser;
  profile: CustomerProfile | null;
  orderCount: number;
  addressCount: number;
  onEdit: () => void;
}

function initials(firstName: string | null, lastName: string | null) {
  const value = `${firstName?.[0] ?? ""}${lastName?.[0] ?? ""}`.trim();
  return value.toUpperCase() || "C";
}

export function AccountCard({
  user,
  profile,
  orderCount,
  addressCount,
  onEdit,
}: AccountCardProps) {
  const firstName = profile?.firstName ?? user.firstName;
  const lastName = profile?.lastName ?? user.lastName;
  const name = `${firstName ?? ""} ${lastName ?? ""}`.trim();
  const displayName = name || "Complete your Craves profile";
  const phone = profile?.registeredPhoneNumber || user.phoneNumber;
  const email = profile?.email ?? user.email;

  return (
    <section
      aria-labelledby="customer-profile-name"
      className="craves-surface overflow-hidden p-6"
    >
      <div className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <div
            aria-hidden="true"
            className="flex h-20 w-20 shrink-0 items-center justify-center rounded-full bg-flame-red font-display text-2xl font-bold text-white"
          >
            {initials(firstName, lastName)}
          </div>
          <div className="min-w-0">
            <p className="craves-overline">Customer account</p>
            <h1
              id="customer-profile-name"
              className="mt-1 truncate font-display text-2xl font-semibold text-ink"
            >
              {displayName}
            </h1>
            <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
              <Phone className="h-4 w-4" aria-hidden="true" />
              <span>{phone}</span>
              <span className="inline-flex items-center gap-1 rounded-full bg-secondary px-2 py-1 text-xs font-semibold text-ink">
                <ShieldCheck className="h-3.5 w-3.5 text-success" aria-hidden="true" />
                Verified
              </span>
            </p>
            {email && (
              <p className="mt-2 flex items-center gap-2 truncate text-sm text-muted-foreground">
                <Mail className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span className="truncate">{email}</span>
              </p>
            )}
            {!profile && (
              <p className="mt-3 max-w-md text-sm leading-6 text-contrast-red">
                Add your first name, last name and optional email so checkout and
                support use the correct details.
              </p>
            )}
          </div>
        </div>

        <button
          type="button"
          onClick={onEdit}
          className="inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-lg border border-contrast-red bg-white px-4 text-sm font-semibold text-contrast-red transition-colors hover:bg-secondary"
          aria-label="Edit customer profile"
        >
          <Pencil className="h-4 w-4" aria-hidden="true" />
          Edit profile
        </button>
      </div>

      <dl className="mt-6 grid grid-cols-3 gap-4 border-t border-border pt-4">
        <div>
          <dt className="text-xs text-muted-foreground">Orders</dt>
          <dd className="mt-1 font-display text-xl font-semibold text-ink">
            {orderCount}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Addresses</dt>
          <dd className="mt-1 font-display text-xl font-semibold text-ink">
            {addressCount}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">Profile</dt>
          <dd className="mt-1 text-sm font-semibold text-ink">
            {profile ? "Complete" : "Action needed"}
          </dd>
        </div>
      </dl>
    </section>
  );
}

export default AccountCard;
