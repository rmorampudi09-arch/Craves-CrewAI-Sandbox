"use client";

import ConfirmationPage from "@/screens/OrderSuccess/OrderSuccess";
import { Suspense } from "react";

export default function ConfirmationRoute() {
  return <Suspense fallback={<div className="min-h-screen bg-cream p-8 text-center text-sm text-muted-foreground">Loading order…</div>}><ConfirmationPage /></Suspense>;
}
