import { Link } from "@tanstack/react-router";
import { ChevronRight, type LucideIcon } from "lucide-react";

interface ProfileLinkCardProps {
  to: string;
  icon: LucideIcon;
  title: string;
  subtitle: string;
}

export function ProfileLinkCard({
  to,
  icon: Icon,
  title,
  subtitle,
}: ProfileLinkCardProps) {
  return (
    <Link
      to={to}
      className="craves-surface group flex min-h-20 items-center justify-between gap-4 p-4 transition-colors hover:border-primary hover:bg-secondary"
    >
      <div className="flex min-w-0 items-center gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-secondary text-contrast-red">
          <Icon className="h-5 w-5" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <h2 className="font-display text-base font-semibold text-ink">
            {title}
          </h2>
          <p className="mt-1 text-sm leading-5 text-muted-foreground">
            {subtitle}
          </p>
        </div>
      </div>
      <ChevronRight
        className="h-5 w-5 shrink-0 text-grey-400 transition-transform group-hover:translate-x-1 group-hover:text-primary"
        aria-hidden="true"
      />
    </Link>
  );
}

export default ProfileLinkCard;
