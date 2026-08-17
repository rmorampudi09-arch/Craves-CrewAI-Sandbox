"use client";

import { registerLicense } from "@syncfusion/ej2-base";

let registered = false;

export function SyncfusionLicense() {
  if (!registered) {
    const key = process.env.NEXT_PUBLIC_SYNCFUSION_LICENSE_KEY?.trim();
    if (key) registerLicense(key);
    registered = true;
  }
  return null;
}
