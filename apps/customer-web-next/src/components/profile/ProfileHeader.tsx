import { Link } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import { CravesLogo } from "@/components/brand/CravesLogo";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";

export function ProfileHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-white/95 backdrop-blur">
      <div className="mx-auto flex min-h-16 max-w-3xl items-center gap-3 px-4 md:px-6">
        <Link
          to="/home"
          className="flex h-11 w-11 items-center justify-center rounded-full text-ink transition-colors hover:bg-secondary"
          aria-label="Back to home"
        >
          <ArrowLeft className="h-5 w-5" aria-hidden="true" />
        </Link>
        <Link
          to="/home"
          className="flex min-h-11 items-center gap-3 rounded-lg pr-3"
          aria-label="Craves home"
        >
          <CravesLogo size="sm" decorative />
          <div>
            <p className="craves-overline">Craves account</p>
            <span className="font-display text-lg font-semibold text-ink">
              Profile
            </span>
          </div>
        </Link>
      </div>
      <div className="mx-auto max-w-3xl px-4 pb-3 md:px-6">
        <PersistentCustomerServiceNav />
      </div>
    </header>
  );
}

export default ProfileHeader;
