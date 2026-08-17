"use client";

export default function ErrorPage({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-cream px-4">
      <div className="max-w-md text-center">
        <h1 className="font-display text-2xl font-bold text-ink">This page didn&apos;t load</h1>
        <p className="mt-2 text-sm text-muted-foreground">Please try again. No payment or order action was repeated automatically.</p>
        <button type="button" onClick={reset} className="btn-primary mt-6">Try again</button>
      </div>
    </main>
  );
}
