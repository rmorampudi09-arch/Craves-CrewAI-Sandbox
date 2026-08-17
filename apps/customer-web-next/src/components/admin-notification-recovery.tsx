"use client";

import { useState } from "react";
import {
  parseNotificationBacklog,
  parseNotificationRecoveryResult,
  type AdminNotificationBacklogItem,
  type AdminNotificationRecoveryResult,
  type NotificationBacklogStatus
} from "@/lib/admin-notification-recovery-contract";

function displayDate(value: string | null): string {
  if (!value) return "Not recorded";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Not recorded"
    : date.toLocaleString("en-IN", { timeZone: "Asia/Kolkata" });
}

function responseErrorCode(value: unknown): string | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const code = (value as Record<string, unknown>).code;
  return typeof code === "string" && code.trim().length > 0 ? code.trim() : null;
}

function failureMessage(status: number, code: string | null): string {
  if (status === 401) return "Administrator session expired. Sign in again.";
  if (status === 403) return "Administrator access is required, or the request origin was rejected.";
  if (status === 404) return "The notification request no longer exists.";
  if (status === 409) return "Only FAILED or DEAD_LETTER requests may be requeued.";
  if (status === 503 || code === "NOTIFICATION_RECOVERY_DISABLED") {
    return "Notification recovery remains disabled until controlled activation.";
  }
  if (status === 400) return "Check the selected backlog, reason and RETRY confirmation.";
  return "Notification recovery is temporarily unavailable.";
}

export function AdminNotificationRecovery() {
  const [statusFilter, setStatusFilter] = useState<NotificationBacklogStatus>("DEAD_LETTER");
  const [items, setItems] = useState<AdminNotificationBacklogItem[]>([]);
  const [selected, setSelected] = useState<AdminNotificationBacklogItem | null>(null);
  const [reason, setReason] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [result, setResult] = useState<AdminNotificationRecoveryResult | null>(null);
  const [message, setMessage] = useState("Load a bounded FAILED or DEAD_LETTER backlog.");
  const [busy, setBusy] = useState(false);

  async function loadBacklog() {
    setBusy(true);
    setResult(null);
    try {
      const response = await fetch(
        `/api/admin/notifications/recovery?status=${statusFilter}&limit=50`,
        { cache: "no-store" }
      );
      const body: unknown = await response.json().catch(() => null);
      const backlog = parseNotificationBacklog(body);
      if (!response.ok || !backlog) {
        throw new Error(failureMessage(response.status, responseErrorCode(body)));
      }
      setItems(backlog);
      setSelected(null);
      setReason("");
      setConfirmation("");
      setMessage(
        `${backlog.length} ${statusFilter} request${backlog.length === 1 ? "" : "s"} loaded.`
      );
    } catch (error) {
      setItems([]);
      setSelected(null);
      setMessage(error instanceof Error ? error.message : "Backlog load failed.");
    } finally {
      setBusy(false);
    }
  }

  async function retry(event: React.FormEvent) {
    event.preventDefault();
    const normalizedReason = reason.replace(/[\r\n]+/g, " ").trim();
    if (!selected) return setMessage("Select one backlog item first.");
    if (normalizedReason.length < 10 || normalizedReason.length > 500) {
      return setMessage("The recovery reason must contain 10–500 characters.");
    }
    if (confirmation !== "RETRY") return setMessage("Type RETRY exactly to confirm requeueing.");
    setBusy(true);
    try {
      const response = await fetch(`/api/admin/notifications/recovery/${selected.requestId}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          requestId: selected.requestId,
          reason: normalizedReason,
          confirmation
        }),
        cache: "no-store"
      });
      const body: unknown = await response.json().catch(() => null);
      const recoveryResult = parseNotificationRecoveryResult(body);
      if (!response.ok || !recoveryResult) {
        throw new Error(failureMessage(response.status, responseErrorCode(body)));
      }
      setResult(recoveryResult);
      setItems(current => current.filter(item => item.requestId !== selected.requestId));
      setSelected(null);
      setReason("");
      setConfirmation("");
      setMessage(
        "The request was moved to PENDING. Provider delivery remains controlled by the separate worker and provider flags."
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Notification retry failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-7">
      <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950">
        <div className="flex flex-wrap items-end gap-4">
          <label className="min-w-56 flex-1 text-sm font-bold">
            Backlog status
            <select
              value={statusFilter}
              onChange={event => setStatusFilter(event.target.value as NotificationBacklogStatus)}
              className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4"
            >
              <option value="DEAD_LETTER">Dead letter</option>
              <option value="FAILED">Failed</option>
            </select>
          </label>
          <button
            type="button"
            onClick={loadBacklog}
            disabled={busy}
            className="min-h-12 rounded-2xl bg-[#6930CA] px-6 font-bold text-white disabled:opacity-50"
          >
            {busy ? "Loading…" : "Load backlog"}
          </button>
        </div>
        <p className="mt-4 text-sm text-slate-600" role="status">
          {message}
        </p>
      </section>

      <div className="grid gap-7 xl:grid-cols-[1.2fr_0.8fr]">
        <section className="space-y-4" aria-label="Notification recovery backlog">
          {items.length ? (
            items.map(item => (
              <button
                key={item.requestId}
                type="button"
                onClick={() => {
                  setSelected(item);
                  setResult(null);
                  setConfirmation("");
                }}
                className={`w-full rounded-[26px] p-5 text-left transition ${
                  selected?.requestId === item.requestId
                    ? "bg-[#F6B545] text-slate-950"
                    : "bg-white text-slate-950 hover:bg-[#FFF8EC]"
                }`}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-bold uppercase tracking-wide">
                      {item.sourceService} · {item.channel}
                    </p>
                    <h3 className="mt-2 text-xl font-bold">{item.eventType}</h3>
                  </div>
                  <span className="rounded-full bg-slate-950/10 px-3 py-1 text-xs font-bold">
                    {item.status}
                  </span>
                </div>
                <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                  <div>
                    <dt className="text-slate-500">Attempts</dt>
                    <dd className="font-bold">{item.attemptCount}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Updated</dt>
                    <dd className="font-bold">{displayDate(item.updatedAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Final code</dt>
                    <dd className="font-bold">{item.finalErrorCode ?? "Not recorded"}</dd>
                  </div>
                </dl>
                {item.lastError && (
                  <p className="mt-4 line-clamp-3 text-sm text-slate-600">{item.lastError}</p>
                )}
                <p className="mt-4 break-all font-mono text-xs text-slate-500">
                  {item.requestId}
                </p>
              </button>
            ))
          ) : (
            <div className="rounded-[30px] border border-dashed border-[#cfc4d7] bg-white p-8 text-slate-600">
              <h2 className="text-2xl font-bold text-slate-950">No backlog loaded</h2>
              <p className="mt-3">
                Only bounded operational fields are shown. Recipient identity and provider payloads are
                intentionally omitted.
              </p>
            </div>
          )}
        </section>

        <form onSubmit={retry} className="h-fit rounded-[30px] bg-white p-6 text-slate-950">
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">
            Audited requeue
          </p>
          <h2 className="mt-3 text-2xl font-bold">Retry one request</h2>
          <p className="mt-3 text-sm leading-6 text-slate-600">
            Requeue changes only the durable request state to PENDING. It does not call FCM, ACS or any
            provider inside this administrator transaction.
          </p>
          <div className="mt-5 rounded-2xl bg-[#FFF8EC] p-4">
            <p className="text-xs font-bold uppercase text-slate-500">Selected request</p>
            <p className="mt-2 break-all font-mono text-xs">{selected?.requestId ?? "None"}</p>
          </div>
          <label className="mt-5 block text-sm font-bold">
            Recovery reason
            <textarea
              value={reason}
              onChange={event => setReason(event.target.value)}
              disabled={!selected}
              minLength={10}
              maxLength={500}
              className="mt-2 min-h-32 w-full rounded-2xl bg-[#FFF8EC] p-4 disabled:opacity-50"
              required
            />
          </label>
          <label className="mt-5 block text-sm font-bold">
            Type RETRY to confirm
            <input
              value={confirmation}
              onChange={event => setConfirmation(event.target.value.toUpperCase())}
              disabled={!selected}
              maxLength={20}
              autoComplete="off"
              className="mt-2 min-h-12 w-full rounded-2xl bg-[#FFF8EC] px-4 font-mono disabled:opacity-50"
              required
            />
          </label>
          <button
            disabled={busy || !selected || confirmation !== "RETRY"}
            className="mt-5 min-h-12 w-full rounded-2xl bg-[#6930CA] font-bold text-white disabled:opacity-40"
          >
            {busy ? "Submitting…" : "Requeue notification"}
          </button>
          {result && (
            <div className="mt-5 rounded-2xl border border-[#6930CA]/20 p-4">
              <p className="text-xs font-bold uppercase text-[#6930CA]">Recovery audit</p>
              <p className="mt-2 font-mono text-xs">{result.recoveryAuditId}</p>
              <p className="mt-2 text-sm">
                {result.previousStatus} → {result.newStatus}
              </p>
              <p className="mt-2 break-all font-mono text-xs">{result.correlationId}</p>
            </div>
          )}
        </form>
      </div>
    </div>
  );
}
