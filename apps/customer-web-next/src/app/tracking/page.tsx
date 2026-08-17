"use client";

import { Suspense } from "react";
import TrackingPage from "@/screens/OrderTracking/OrderTracking";

function TrackingFallback() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-cream px-4">
      <div className="text-center" role="status">
        <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-border border-t-primary" />
        <p className="mt-4 text-sm font-medium text-muted-foreground">
          Loading order tracking…
        </p>
      </div>
    </main>
  );
}

export default function TrackingRoute() {
  return (
    <Suspense fallback={<TrackingFallback />}>
      <TrackingPage />
    </Suspense>
  );
}
