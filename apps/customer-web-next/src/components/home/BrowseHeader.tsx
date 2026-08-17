import { Link } from "@tanstack/react-router";
import {
  LogOut,
  MapPin,
  Search,
  ShoppingCart,
  UserCircle,
} from "lucide-react";
import { CravesLogo } from "@/components/brand/CravesLogo";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";
import type { CravesUser } from "@/services/auth/cravesAuth";

interface BrowseHeaderProps {
  user: CravesUser;
  locationLabel: string;
  onOpenLocation: () => void;
  cartCount: number;
  onOpenCart: () => void;
  onLogout: () => void;
  searchTerm: string;
  onSearchTermChange: (value: string) => void;
}

const actionLinkClass =
  "border-[#C92716] bg-[#C92716] text-black transition-colors hover:border-[#F62E18] hover:bg-[#F62E18] hover:font-bold hover:text-white focus-visible:border-[#F62E18] focus-visible:bg-[#F62E18] focus-visible:font-bold focus-visible:text-white";

export function BrowseHeader({
  user,
  locationLabel,
  onOpenLocation,
  cartCount,
  onOpenCart,
  onLogout,
  searchTerm,
  onSearchTermChange,
}: BrowseHeaderProps) {
  const firstName = user.firstName || user.username.split(" ")[0] || "there";

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-white/95 shadow-[0_1px_0_rgba(0,0,0,0.04)] backdrop-blur-xl">
      <div className="mx-auto max-w-7xl px-4 md:px-6">
        <div className="flex min-h-18 items-center gap-3 py-3">
          <Link to="/home" className="flex min-h-11 shrink-0 items-center gap-3 rounded-lg pr-2" aria-label="Craves discovery home">
            <CravesLogo size="md" />
            <span className="hidden leading-tight sm:block">
              <span className="block font-display text-xl font-bold tracking-[-0.04em] text-ink">Craves</span>
              <span className="block text-[0.62rem] font-semibold uppercase tracking-[0.18em] text-muted-foreground">Food from home</span>
            </span>
          </Link>

          <button
            type="button"
            onClick={onOpenLocation}
            className="hidden min-h-11 min-w-0 max-w-xs items-center gap-2 rounded-lg border px-3 text-left text-sm md:flex"
          >
            <MapPin className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span className="min-w-0">
              <span className="block text-[0.62rem] font-semibold uppercase tracking-[0.08em]">Deliver to</span>
              <span className="block truncate font-semibold">{locationLabel}</span>
            </span>
          </button>

          <div className="ml-auto flex items-center gap-2">
            <span className="hidden text-sm font-semibold text-ink lg:inline">Hi, {firstName}</span>
            <Link
              to="/profile"
              className={`flex min-h-11 items-center gap-2 rounded-lg border px-3 text-sm font-semibold ${actionLinkClass}`}
            >
              <UserCircle className="h-5 w-5" aria-hidden="true" />
              <span className="hidden sm:inline">Profile</span>
            </Link>
            <button
              type="button"
              onClick={onOpenCart}
              className="relative flex h-11 w-11 items-center justify-center rounded-lg border shadow-[var(--shadow-card)]"
              aria-label={`Open cart${cartCount ? ` with ${cartCount} items` : ""}`}
            >
              <ShoppingCart className="h-5 w-5" aria-hidden="true" />
              {cartCount > 0 && (
                <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full border border-[#C92716] bg-white px-1 text-[0.62rem] font-bold text-black">
                  {cartCount}
                </span>
              )}
            </button>
            <button
              type="button"
              onClick={onLogout}
              className="hidden h-11 w-11 items-center justify-center rounded-lg border lg:flex"
              aria-label="Sign out"
            >
              <LogOut className="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
        </div>

        <div className="grid gap-3 pb-3 lg:grid-cols-[minmax(18rem,1fr)_auto] lg:items-center">
          <label className="flex min-h-12 items-center gap-3 rounded-xl border border-border bg-white px-4 focus-within:border-[#F62E18]">
            <Search className="h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span className="sr-only">Search dishes or kitchens</span>
            <input
              value={searchTerm}
              onChange={(event) => onSearchTermChange(event.target.value)}
              placeholder="Search dishes or home kitchens"
              className="w-full bg-transparent text-base text-ink outline-none placeholder:text-[#9A9A95]"
              type="search"
              autoComplete="off"
            />
          </label>
          <PersistentCustomerServiceNav className="lg:justify-end lg:pb-0" />
        </div>

        <button
          type="button"
          onClick={onOpenLocation}
          className="mb-3 flex min-h-11 w-full items-center gap-2 rounded-lg border px-3 text-left text-sm md:hidden"
        >
          <MapPin className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span className="truncate">{locationLabel}</span>
        </button>
      </div>
    </header>
  );
}

export default BrowseHeader;
