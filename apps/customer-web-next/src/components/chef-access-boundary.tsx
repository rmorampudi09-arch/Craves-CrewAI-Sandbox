"use client";

import Link from "next/link";
import { type ReactNode, useEffect, useState } from "react";
import {
  loadSession,
  synchronizeSessionRoles,
  type CravesUser,
} from "@/services/auth/cravesAuth";

type AccessState = "synchronizing" | "ready" | "sign-in" | "not-approved";

function hasChefRole(user: CravesUser | null): boolean {
  return Boolean(user?.roles.some(role => role.toUpperCase() === "CHEF"));
}

export function ChefAccessBoundary({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AccessState>("synchronizing");

  useEffect(() => {
    let active = true;
    void (async () => {
      const current = await loadSession();
      if (!active) return;
      if (!current) {
        setState("sign-in");
        return;
      }
      if (!hasChefRole(current)) {
        setState("not-approved");
        return;
      }

      // Auth /me reads the current database roles. Rotate the HTTP-only token
      // before calling Catalog or Order so its signed JWT carries CHEF too.
      const synchronized = await synchronizeSessionRoles();
      if (!active) return;
      setState(hasChefRole(synchronized) ? "ready" : "sign-in");
    })().catch(() => {
      if (active) setState("sign-in");
    });
    return () => {
      active = false;
    };
  }, []);

  if (state === "ready") return <>{children}</>;

  return (
    <section className="rounded-[30px] bg-[#FFF8EC] p-7 text-slate-950">
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">
        Secure chef access
      </p>
      <h2 className="mt-3 text-2xl font-bold">
        {state === "synchronizing"
          ? "Synchronizing your approved chef role…"
          : state === "not-approved"
            ? "Chef approval is still required"
            : "Sign in again to continue"}
      </h2>
      {state !== "synchronizing" && (
        <p className="mt-3 text-sm leading-6 text-slate-600">
          {state === "not-approved"
            ? "You are signed in. Submit or review your chef application; Craves admin approval remains authoritative."
            : "Complete mobile OTP sign-in again so Catalog and Order services receive your current roles."}
        </p>
      )}
      {state === "sign-in" && (
        <Link
          href="/sign-in?returnTo=/chef"
          className="mt-5 inline-flex rounded-full bg-[#6930CA] px-5 py-3 font-bold text-white"
        >
          Continue with mobile OTP
        </Link>
      )}
      {state === "not-approved" && (
        <Link
          href="/chef/application"
          className="mt-5 inline-flex rounded-full bg-[#6930CA] px-5 py-3 font-bold text-white"
        >
          Open chef application
        </Link>
      )}
    </section>
  );
}
