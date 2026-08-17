import Link from "next/link";

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-cream px-4">
      <div className="max-w-md text-center">
        <p className="font-display text-7xl font-bold text-primary">404</p>
        <h1 className="mt-4 font-display text-2xl font-bold text-ink">Page not found</h1>
        <p className="mt-2 text-sm text-muted-foreground">The page does not exist or has moved.</p>
        <Link href="/" className="btn-primary mt-6 inline-flex">Go home</Link>
      </div>
    </main>
  );
}
