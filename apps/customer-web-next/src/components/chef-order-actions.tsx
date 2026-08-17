"use client";

import { useState } from "react";
import {
  CheckCircle2,
  Clock3,
  LoaderCircle,
  PackageCheck,
  XCircle,
} from "lucide-react";
import {
  parseChefOrderResponse,
  type ChefOrder,
} from "@/lib/chef-order-contract";

function responseMessage(value: unknown, fallback: string): string {
  return value &&
    typeof value === "object" &&
    "message" in value &&
    typeof value.message === "string"
    ? value.message
    : fallback;
}

export function ChefOrderActions({
  order,
  onUpdated,
}: {
  order: ChefOrder;
  onUpdated(order: ChefOrder): void;
}) {
  const [prepTimeMinutes, setPrepTimeMinutes] = useState(
    order.prepTimeMinutes ? String(order.prepTimeMinutes) : "30",
  );
  const [note, setNote] = useState(order.chefResponseNote ?? "");
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busyAction, setBusyAction] = useState<string | null>(null);

  async function act(path: string, body?: Record<string, unknown>) {
    if (busyAction) return;
    setBusyAction(path);
    setMessage("Submitting the chef action to Order Service…");
    setError("");
    try {
      const response = await fetch(`/api/chef/orders/${order.id}/${path}`, {
        method: "POST",
        credentials: "same-origin",
        headers: body ? { "Content-Type": "application/json" } : undefined,
        body: body ? JSON.stringify(body) : undefined,
      });
      const result = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(
          response.status === 409
            ? "The order state changed or the action window is no longer valid. Refresh this order."
            : responseMessage(result, "The chef action could not be completed."),
        );
      }
      const updated = parseChefOrderResponse(result);
      if (!updated || updated.id.toLowerCase() !== order.id.toLowerCase()) {
        throw new Error("Craves returned an invalid updated order response.");
      }
      onUpdated(updated);
      setMessage("Order Service applied the chef action.");
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The chef action could not be completed.",
      );
      setMessage("");
    } finally {
      setBusyAction(null);
    }
  }

  if (order.status === "CHEF_ACCEPTANCE_PENDING") {
    const prep = Number(prepTimeMinutes);
    const validPrep = Number.isInteger(prep) && prep >= 1 && prep <= 1_440;
    const singleBorderField = "mt-2 w-full rounded-xl border border-border bg-white text-base text-ink outline-none ring-0 focus:border-[#F62E18] focus:outline-none focus:ring-0";
    return (
      <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
        <div className="flex items-start gap-3">
          <Clock3 className="mt-1 h-6 w-6 shrink-0 text-primary" aria-hidden="true" />
          <div>
            <p className="craves-overline text-primary">Action required</p>
            <h2 className="mt-1 font-display text-2xl font-bold text-ink">
              Accept or reject this order
            </h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              The acceptance deadline and the current order state remain authoritative on the backend. Every action uses a unique correlation and idempotency key.
            </p>
          </div>
        </div>

        <div className="mt-5 grid gap-4 md:grid-cols-2">
          <label className="text-sm font-semibold text-ink">
            Preparation time in minutes
            <input
              type="number"
              inputMode="numeric"
              min={1}
              max={1440}
              value={prepTimeMinutes}
              onChange={(event) => setPrepTimeMinutes(event.target.value)}
              className={`${singleBorderField} min-h-12 px-4`}
              data-craves-single-border="true"
            />
          </label>
          <label className="text-sm font-semibold text-ink">
            Optional acceptance note
            <input
              value={note}
              maxLength={500}
              onChange={(event) => setNote(event.target.value)}
              className={`${singleBorderField} min-h-12 px-4`}
              placeholder="Preparation or packing note"
              data-craves-single-border="true"
            />
          </label>
        </div>

        <label className="mt-4 block text-sm font-semibold text-ink">
          Rejection reason
          <textarea
            value={reason}
            maxLength={500}
            onChange={(event) => setReason(event.target.value)}
            className={`${singleBorderField} min-h-24 p-4`}
            placeholder="Required only when rejecting"
            data-craves-single-border="true"
          />
        </label>

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <button
            type="button"
            disabled={Boolean(busyAction) || !validPrep}
            onClick={() =>
              void act("accept", {
                prepTimeMinutes: prep,
                note: note.trim() || null,
                actionId: crypto.randomUUID(),
              })
            }
            className="btn-primary min-h-12 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busyAction === "accept" ? (
              <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
            )}
            {busyAction === "accept" ? "Accepting…" : "Accept order"}
          </button>
          <button
            type="button"
            disabled={Boolean(busyAction) || reason.trim().length < 3}
            onClick={() =>
              void act("reject", {
                reason: reason.trim(),
                actionId: crypto.randomUUID(),
              })
            }
            className="inline-flex min-h-12 items-center justify-center gap-2 rounded-lg border px-5 text-sm disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busyAction === "reject" ? (
              <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <XCircle className="h-4 w-4" aria-hidden="true" />
            )}
            {busyAction === "reject" ? "Rejecting…" : "Reject order"}
          </button>
        </div>

        {!validPrep && (
          <p className="mt-3 text-xs font-medium text-error">
            Enter a preparation time between 1 and 1,440 minutes.
          </p>
        )}
        {message && (
          <p role="status" className="mt-4 rounded-xl bg-secondary p-3 text-sm text-muted-foreground">
            {message}
          </p>
        )}
        {error && (
          <p role="alert" className="mt-4 rounded-xl border border-error/20 bg-error/5 p-3 text-sm font-medium text-error">
            {error}
          </p>
        )}
      </section>
    );
  }

  if (order.status === "CHEF_ACCEPTED" || order.status === "PREPARING") {
    return (
      <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
        <div className="flex items-start gap-3">
          <PackageCheck className="mt-1 h-6 w-6 shrink-0 text-primary" aria-hidden="true" />
          <div>
            <p className="craves-overline text-primary">Preparation workflow</p>
            <h2 className="mt-1 font-display text-2xl font-bold text-ink">
              Mark the complete order ready
            </h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              Use this only after every item is prepared, packed and available for the delivery pickup workflow.
            </p>
          </div>
        </div>
        <button
          type="button"
          disabled={Boolean(busyAction)}
          onClick={() => void act("ready-for-pickup")}
          className="btn-primary mt-5 min-h-12 disabled:cursor-wait disabled:opacity-50"
        >
          {busyAction ? (
            <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
          ) : (
            <PackageCheck className="h-4 w-4" aria-hidden="true" />
          )}
          {busyAction ? "Updating…" : "Mark ready for pickup"}
        </button>
        {message && (
          <p role="status" className="mt-4 rounded-xl bg-secondary p-3 text-sm text-muted-foreground">
            {message}
          </p>
        )}
        {error && (
          <p role="alert" className="mt-4 rounded-xl border border-error/20 bg-error/5 p-3 text-sm font-medium text-error">
            {error}
          </p>
        )}
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
      <p className="craves-overline text-primary">Order workflow</p>
      <h2 className="mt-1 font-display text-xl font-bold text-ink">
        No chef action is available
      </h2>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">
        The current backend status does not permit another chef transition. Delivery, refund and terminal states are controlled by their owning services.
      </p>
    </section>
  );
}
