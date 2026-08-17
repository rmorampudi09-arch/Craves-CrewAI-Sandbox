"use client";

import { useState } from "react";
import type {
  AdminInvestigationResource,
  AdminInvestigationResult
} from "@/lib/admin-investigation-contract";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const RESOURCES: Array<{ value: AdminInvestigationResource; label: string; hint: string }> = [
  { value: "order", label: "Order", hint: "Order UUID" },
  { value: "payment", label: "Payment", hint: "Payment-order UUID" },
  { value: "refund", label: "Refund", hint: "Refund UUID" },
  { value: "delivery-command", label: "Delivery command", hint: "Delivery-command UUID" }
];

function displayDate(value: string | null): string {
  if (!value) return "Time not recorded";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Time not recorded" : date.toLocaleString("en-IN", { timeZone: "Asia/Kolkata" });
}

function statusMessage(responseStatus: number, code: string | null): string {
  if (responseStatus === 401) return "Administrator session expired. Sign in again.";
  if (responseStatus === 403) return "Administrator access is required, or the request origin was rejected.";
  if (responseStatus === 404) return "No matching backend record was found.";
  if (responseStatus === 400) return "Check the resource UUID and provide an audit reason of 10–500 characters.";
  if (code === "INVALID_INVESTIGATION_RESPONSE") return "The backend returned an unexpected contract. No raw response was displayed.";
  return "The investigation service is temporarily unavailable.";
}

export function AdminOperationalInvestigator() {
  const [resource, setResource] = useState<AdminInvestigationResource>("order");
  const [resourceId, setResourceId] = useState("");
  const [reason, setReason] = useState("");
  const [result, setResult] = useState<AdminInvestigationResult | null>(null);
  const [message, setMessage] = useState("Choose a resource and enter its exact UUID.");
  const [busy, setBusy] = useState(false);

  async function investigate(event: React.FormEvent) {
    event.preventDefault();
    const normalizedReason = reason.replace(/[\r\n]+/g, " ").trim();
    if (!UUID.test(resourceId)) {
      setResult(null);
      setMessage("Enter a valid UUID for the selected resource.");
      return;
    }
    if (normalizedReason.length < 10 || normalizedReason.length > 500) {
      setResult(null);
      setMessage("The operational reason must contain 10–500 characters.");
      return;
    }

    setBusy(true);
    setMessage("");
    try {
      const response = await fetch("/api/admin/operations/investigate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ resource, resourceId, reason: normalizedReason }),
        cache: "no-store"
      });
      const body = await response.json().catch(() => null) as (AdminInvestigationResult & { code?: string }) | null;
      if (!response.ok || !body || typeof body.correlationId !== "string") {
        throw new Error(statusMessage(response.status, body?.code ?? null));
      }
      setResult(body);
      setMessage("Read-only evidence loaded. The owning service recorded the reason and correlation ID.");
    } catch (error) {
      setResult(null);
      setMessage(error instanceof Error ? error.message : "Investigation failed.");
    } finally {
      setBusy(false);
    }
  }

  const selected = RESOURCES.find(option => option.value === resource) ?? RESOURCES[0];

  return <div className="grid gap-7 lg:grid-cols-[0.72fr_1.28fr]">
    <form onSubmit={investigate} className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950">
      <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">Audited read only</p>
      <h2 className="mt-3 text-2xl font-bold">Investigate an operation</h2>
      <p className="mt-3 text-sm leading-6 text-slate-600">Every successful lookup is re-authorized and audit-recorded by the owning Spring service. This screen cannot retry, refund, book, cancel, suspend or change any business state.</p>
      <label className="mt-5 block text-sm font-bold">Resource
        <select value={resource} onChange={event => { setResource(event.target.value as AdminInvestigationResource); setResult(null); }} className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4">
          {RESOURCES.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </label>
      <label className="mt-5 block text-sm font-bold">{selected.hint}
        <input value={resourceId} onChange={event => setResourceId(event.target.value.trim())} maxLength={64} autoComplete="off" spellCheck={false} className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4 font-mono text-sm" required />
      </label>
      <label className="mt-5 block text-sm font-bold">Operational reason
        <textarea value={reason} onChange={event => setReason(event.target.value)} minLength={10} maxLength={500} className="mt-2 min-h-32 w-full rounded-2xl bg-white p-4" placeholder="Example: Investigating support case CRV-2026-001 after customer escalation." required />
      </label>
      <button disabled={busy} className="mt-5 min-h-12 w-full rounded-2xl bg-[#6930CA] font-bold text-white disabled:opacity-50">{busy ? "Loading evidence…" : "Run read-only investigation"}</button>
      {message && <p className="mt-4 text-sm leading-6 text-slate-600" role="status">{message}</p>}
    </form>

    <section aria-live="polite">
      {result ? <div className="space-y-6">
        <div className="rounded-[30px] bg-white p-6 text-slate-950">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">{result.resource.replaceAll("-", " ")}</p><h2 className="mt-2 text-3xl font-bold">{result.title}</h2><p className="mt-2 text-sm text-slate-500">Status: <strong className="text-slate-900">{result.status ?? "Not recorded"}</strong></p></div>
            <span className="rounded-full bg-[#FFF8EC] px-4 py-2 font-mono text-xs">{result.resourceId}</span>
          </div>
          <dl className="mt-7 grid gap-4 sm:grid-cols-2">{result.summary.map(entry => <div key={`${entry.label}-${entry.value}`} className="rounded-2xl bg-[#FFF8EC] p-4"><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">{entry.label}</dt><dd className="mt-2 break-words text-sm font-semibold">{entry.value}</dd></div>)}</dl>
          <div className="mt-6 rounded-2xl border border-[#6930CA]/20 p-4"><p className="text-xs font-bold uppercase tracking-wide text-[#6930CA]">Audit correlation</p><p className="mt-2 break-all font-mono text-sm">{result.correlationId}</p></div>
        </div>
        <div className="rounded-[30px] bg-white p-6 text-slate-950"><h3 className="text-2xl font-bold">Operational timeline</h3>{result.timeline.length ? <ol className="mt-5 space-y-4">{result.timeline.map((entry, index) => <li key={`${entry.label}-${entry.occurredAt}-${index}`} className="border-l-4 border-[#F6B545] pl-4"><div className="flex flex-wrap justify-between gap-2"><strong>{entry.label}</strong><time className="text-xs text-slate-500">{displayDate(entry.occurredAt)}</time></div><p className="mt-1 text-sm text-slate-600">{entry.status ?? "No status"}{entry.detail ? ` · ${entry.detail}` : ""}</p></li>)}</ol> : <p className="mt-4 text-sm text-slate-600">No bounded history entries were returned.</p>}</div>
      </div> : <div className="rounded-[30px] border border-dashed border-[#cfc4d7] bg-white p-8 text-slate-600"><h2 className="text-2xl font-bold text-slate-950">No evidence loaded</h2><p className="mt-3 leading-7">A validated, privacy-reduced backend result will appear here. Raw provider and webhook payloads are never rendered.</p></div>}
    </section>
  </div>;
}
