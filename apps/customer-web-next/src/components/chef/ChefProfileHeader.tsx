import { ArrowLeft } from "lucide-react";

/** Simple back button header for the chef profile page. */
export function ChefProfileHeader({ onBack }: { onBack: () => void }) {
  return (
    <header className="border-b border-border bg-cream/90">
      <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-4">
        <button
          type="button"
          onClick={onBack}
          className="rounded-full p-2 hover:bg-black/5"
          aria-label="Back"
        >
          <ArrowLeft className="h-5 w-5 text-ink" />
        </button>
        <span className="font-display text-lg font-bold text-primary">Chef Profile</span>
      </div>
    </header>
  );
}

export default ChefProfileHeader;
