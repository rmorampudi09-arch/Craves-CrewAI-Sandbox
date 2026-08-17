"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  BadgeIndianRupee,
  Clock3,
  FileCheck2,
  RefreshCw,
  RotateCcw,
  WalletCards,
} from "lucide-react";
import {
  formatChefEarningStatus,
  parseChefEarnings,
  type ChefEarning,
  type ChefEarningStatus,
} from "@/lib/chef-earnings-contract";

type LedgerView = "ALL" | ChefEarningStatus;

function money(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

function statusClass(status: ChefEarningStatus): string {
  if (status === "SETTLED") return "bg-success/10 text-success";
  if (status === "REVERSED") return "bg-error/10 text-error";
  if (status === "SETTLEMENT_PENDING") return "bg-warning/10 text-warning";
  return "bg-secondary text-contrast-red";
}

export function ChefEarningsLedger() {
  const [entries, setEntries] = useState<ChefEarning[]>([]);
  const [view, setView] = useState<LedgerView>("ALL");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const response = await fetch("/api/chef/earnings", {
        cache: "no-store",
        credentials: "same-origin",
      });
      const raw = await response.json().catch(() => null);
      if (!response.ok) {
        const message =
          raw &&
          typeof raw === "object" &&
          "message" in raw &&
          typeof raw.message === "string"
            ? raw.message
            : "Chef earnings are temporarily unavailable.";
        throw new Error(message);
      }
      const parsed = parseChefEarnings(raw);
      if (!parsed) throw new Error("Craves returned an invalid chef earnings response.");
      setEntries(
        [...parsed].sort(
          (left, right) =>
            new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
        ),
      );
      setLastUpdatedAt(new Date());
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Chef earnings are temporarily unavailable.",
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleEntries = useMemo(
    () =>
      view === "ALL"
        ? entries
        : entries.filter((entry) => entry.status === view),
    [entries, view],
  );

  const currency = entries[0]?.currency ?? "INR";
  const approvedPayable = entries
    .filter((entry) =>
      ["APPROVED", "SETTLEMENT_PENDING", "SETTLED"].includes(entry.status),
    )
    .reduce((sum, entry) => sum + entry.netPayable, 0);
  const settledPayable = entries
    .filter((entry) => entry.status === "SETTLED")
    .reduce((sum, entry) => sum + entry.netPayable, 0);
  const pendingPayable = entries
    .filter((entry) => entry.status === "SETTLEMENT_PENDING")
    .reduce((sum, entry) => sum + entry.netPayable, 0);

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        {[
          {
            label: "Approved net payable",
            value: money(approvedPayable, currency),
            icon: FileCheck2,
          },
          {
            label: "Settlement pending",
            value: money(pendingPayable, currency),
            icon: Clock3,
          },
          {
            label: "Recorded settled",
            value: money(settledPayable, currency),
            icon: WalletCards,
          },
        ].map((metric) => (
          <article
            key={metric.label}
            className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]"
          >
            <metric.icon className="h-5 w-5 text-primary" aria-hidden="true" />
            <p className="mt-4 text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
              {metric.label}
            </p>
            <p className="mt-1 font-display text-2xl font-bold text-ink">
              {metric.value}
            </p>
          </article>
        ))}
      </section>

      <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="craves-overline text-primary">Audited financial ledger</p>
            <h2 className="mt-1 font-display text-2xl font-bold tracking-[-0.035em] text-ink">
              Chef earning allocations
            </h2>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">
              These values are entered and approved by Craves finance/admin. The browser never calculates commission, tax withholding or payout timing.
            </p>
          </div>
          <button
            type="button"
            disabled={refreshing || loading}
            onClick={() => void load(true)}
            className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-border px-4 text-sm font-semibold text-ink hover:border-primary disabled:opacity-50"
          >
            <RefreshCw
              className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`}
              aria-hidden="true"
            />
            Refresh
          </button>
        </div>

        <div className="mt-5 flex gap-2 overflow-x-auto pb-1" aria-label="Filter earning ledger">
          {(
            [
              "ALL",
              "DRAFT",
              "APPROVED",
              "SETTLEMENT_PENDING",
              "SETTLED",
              "REVERSED",
            ] as const
          ).map((status) => (
            <button
              key={status}
              type="button"
              onClick={() => setView(status)}
              aria-pressed={view === status}
              className={`min-h-11 shrink-0 rounded-full border px-4 text-sm font-semibold ${
                view === status
                  ? "border-primary bg-primary text-white"
                  : "border-border bg-white text-ink hover:border-primary"
              }`}
            >
              {status === "ALL"
                ? "All"
                : formatChefEarningStatus(status)}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="mt-6 space-y-3" aria-hidden="true">
            {Array.from({ length: 4 }, (_, index) => (
              <div key={index} className="h-36 animate-pulse rounded-2xl bg-grey-200" />
            ))}
          </div>
        ) : error && entries.length === 0 ? (
          <div className="mt-6 rounded-2xl border border-error/20 bg-error/5 p-8 text-center">
            <AlertTriangle className="mx-auto h-9 w-9 text-error" aria-hidden="true" />
            <h3 className="mt-4 font-display text-xl font-bold text-ink">
              Earnings ledger unavailable
            </h3>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
              {error}
            </p>
            <button type="button" onClick={() => void load()} className="btn-primary mt-6">
              <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
            </button>
          </div>
        ) : entries.length === 0 ? (
          <div className="mt-6 rounded-2xl border border-dashed border-border bg-cream p-8 text-center">
            <BadgeIndianRupee className="mx-auto h-10 w-10 text-muted-foreground" aria-hidden="true" />
            <h3 className="mt-4 font-display text-xl font-bold text-ink">
              No earning allocations yet
            </h3>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
              Delivered orders do not automatically become payout entries. Craves finance must create and approve an allocation using the approved commission and tax policy.
            </p>
          </div>
        ) : visibleEntries.length === 0 ? (
          <div className="mt-6 rounded-2xl border border-dashed border-border bg-cream p-8 text-center text-sm text-muted-foreground">
            No ledger entries match this status.
          </div>
        ) : (
          <div className="mt-6 space-y-4">
            {visibleEntries.map((entry) => (
              <article
                key={entry.id}
                className="rounded-2xl border border-border bg-cream p-5"
              >
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${statusClass(entry.status)}`}>
                      {formatChefEarningStatus(entry.status)}
                    </span>
                    <h3 className="mt-3 font-display text-lg font-bold text-ink">
                      Order #{entry.orderId.slice(-8).toUpperCase()}
                    </h3>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {entry.orderSource.replaceAll("_", " ")} · {entry.allocationReference}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                      Net payable
                    </p>
                    <p className="font-display text-2xl font-bold text-ink">
                      {money(entry.netPayable, entry.currency)}
                    </p>
                  </div>
                </div>

                <dl className="mt-5 grid gap-3 border-t border-border pt-4 text-sm sm:grid-cols-4">
                  <div>
                    <dt className="text-xs text-muted-foreground">Gross</dt>
                    <dd className="mt-1 font-semibold text-ink">{money(entry.grossAmount, entry.currency)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">Commission</dt>
                    <dd className="mt-1 font-semibold text-ink">-{money(entry.commissionAmount, entry.currency)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">Tax withheld</dt>
                    <dd className="mt-1 font-semibold text-ink">-{money(entry.taxWithheldAmount, entry.currency)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">Adjustment</dt>
                    <dd className="mt-1 font-semibold text-ink">{money(entry.adjustmentAmount, entry.currency)}</dd>
                  </div>
                </dl>

                <p className="mt-4 rounded-xl bg-white p-3 text-xs leading-5 text-muted-foreground">
                  Finance note: {entry.reason}
                </p>
                <p className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
                  {entry.status === "REVERSED" ? (
                    <RotateCcw className="h-3.5 w-3.5 text-error" aria-hidden="true" />
                  ) : (
                    <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
                  )}
                  Updated {new Date(entry.updatedAt).toLocaleString("en-IN")}
                </p>
              </article>
            ))}
          </div>
        )}

        {lastUpdatedAt && (
          <p className="mt-5 text-xs text-muted-foreground">
            Last refreshed {lastUpdatedAt.toLocaleTimeString("en-IN")}
          </p>
        )}
        {error && entries.length > 0 && (
          <p role="alert" className="mt-4 rounded-xl border border-error/20 bg-error/5 p-3 text-sm font-medium text-error">
            {error}
          </p>
        )}
      </section>
    </div>
  );
}

export default ChefEarningsLedger;
