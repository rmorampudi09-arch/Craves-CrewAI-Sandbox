"use client";

import { useState } from "react";
import type {
  AdminAccountAction,
  AdminAccountInterventionStatus
} from "@/lib/admin-account-intervention-contract";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function displayDate(value: string | null): string {
  if (!value) return "Not recorded";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Not recorded" : date.toLocaleString("en-IN", { timeZone: "Asia/Kolkata" });
}

function failureMessage(status: number, code: string | null): string {
  if (status === 401) return "Administrator session expired. Sign in again.";
  if (status === 403) return "Administrator access is required, or the request origin was rejected.";
  if (status === 404) return "No identity exists for that UUID.";
  if (status === 409) return "The backend rejected this intervention, including self-suspension or an invalid state transition.";
  if (status === 503 || code === "ACCOUNT_INTERVENTION_DISABLED") return "Account intervention remains disabled until the controlled production activation step.";
  if (status === 400) return "Check the identity UUID, reason and typed confirmation.";
  return "The account intervention service is temporarily unavailable.";
}

export function AdminAccountIntervention() {
  const [identityId, setIdentityId] = useState("");
  const [status, setStatus] = useState<AdminAccountInterventionStatus | null>(null);
  const [action, setAction] = useState<AdminAccountAction>("SUSPEND");
  const [reason, setReason] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [message, setMessage] = useState("Load an exact identity UUID before any intervention is allowed.");
  const [busy, setBusy] = useState(false);

  async function request(path: string, init?: RequestInit): Promise<AdminAccountInterventionStatus> {
    const response = await fetch(path, { ...init, cache: "no-store" });
    const body = await response.json().catch(() => null) as (AdminAccountInterventionStatus & { code?: string }) | null;
    if (!response.ok || !body || typeof body.identityId !== "string") {
      throw new Error(failureMessage(response.status, body?.code ?? null));
    }
    return body;
  }

  async function loadStatus(event: React.FormEvent) {
    event.preventDefault();
    const normalizedIdentityId = identityId.trim().toLowerCase();
    if (!UUID.test(normalizedIdentityId)) {
      setStatus(null);
      setMessage("Enter a valid identity UUID.");
      return;
    }
    setIdentityId(normalizedIdentityId);
    setBusy(true);
    try {
      const result = await request(`/api/admin/accounts/${normalizedIdentityId}`);
      setStatus(result);
      setConfirmation("");
      setMessage("Identity status loaded. Review the masked account evidence before choosing an action.");
    } catch (error) {
      setStatus(null);
      setMessage(error instanceof Error ? error.message : "Status lookup failed.");
    } finally {
      setBusy(false);
    }
  }

  async function intervene(event: React.FormEvent) {
    event.preventDefault();
    const normalizedIdentityId = identityId.trim().toLowerCase();
    const normalizedReason = reason.replace(/[\r\n]+/g, " ").trim();
    if (!status || status.identityId.toLowerCase() !== normalizedIdentityId) {
      setMessage("Reload the identity status before intervening.");
      return;
    }
    if (normalizedReason.length < 10 || normalizedReason.length > 500) {
      setMessage("The intervention reason must contain 10–500 characters.");
      return;
    }
    if (confirmation !== action) {
      setMessage(`Type ${action} exactly to confirm this high-impact action.`);
      return;
    }

    setBusy(true);
    try {
      const result = await request(`/api/admin/accounts/${normalizedIdentityId}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ identityId: normalizedIdentityId, action, reason: normalizedReason, confirmation })
      });
      setIdentityId(result.identityId.toLowerCase());
      setStatus(result);
      setReason("");
      setConfirmation("");
      setMessage(result.changed
        ? `${action} was accepted locally. Firebase synchronization status is shown below.`
        : "No local state change was required; the audited result is shown below.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Intervention failed.");
    } finally {
      setBusy(false);
    }
  }

  return <div className="grid gap-7 xl:grid-cols-[0.78fr_1.22fr]">
    <div className="space-y-6">
      <form onSubmit={loadStatus} className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950">
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">Step 1 · Read only</p>
        <h2 className="mt-3 text-2xl font-bold">Load account status</h2>
        <p className="mt-3 text-sm leading-6 text-slate-600">Only an exact backend identity UUID is accepted. Phone and Firebase identifiers are never requested or displayed.</p>
        <label className="mt-5 block text-sm font-bold">Identity UUID
          <input value={identityId} onChange={event => { setIdentityId(event.target.value.trim().toLowerCase()); setStatus(null); }} autoComplete="off" spellCheck={false} maxLength={64} className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4 font-mono text-sm" required />
        </label>
        <button disabled={busy} className="mt-5 min-h-12 w-full rounded-2xl bg-[#6930CA] font-bold text-white disabled:opacity-50">{busy ? "Checking…" : "Load audited status"}</button>
      </form>

      <form onSubmit={intervene} className="rounded-[30px] border border-amber-200 bg-white p-6 text-slate-950">
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#F6B545]">Step 2 · High impact</p>
        <h2 className="mt-3 text-2xl font-bold">Suspend or reactivate</h2>
        <p className="mt-3 text-sm leading-6 text-slate-600">Suspension revokes active Craves refresh sessions and increments the token version. Firebase disable/enable is completed by the durable worker after activation.</p>
        <label className="mt-5 block text-sm font-bold">Action
          <select value={action} onChange={event => { setAction(event.target.value as AdminAccountAction); setConfirmation(""); }} className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4 text-slate-950">
            <option value="SUSPEND">Suspend account</option>
            <option value="REACTIVATE">Reactivate account</option>
          </select>
        </label>
        <label className="mt-5 block text-sm font-bold">Mandatory audit reason
          <textarea value={reason} onChange={event => setReason(event.target.value)} minLength={10} maxLength={500} disabled={!status} className="mt-2 min-h-32 w-full rounded-2xl bg-white p-4 text-slate-950 disabled:opacity-50" required />
        </label>
        <label className="mt-5 block text-sm font-bold">Type {action} to confirm
          <input value={confirmation} onChange={event => setConfirmation(event.target.value.toUpperCase())} disabled={!status} autoComplete="off" maxLength={20} className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4 font-mono text-slate-950 disabled:opacity-50" required />
        </label>
        <button disabled={busy || !status || confirmation !== action} className="mt-5 min-h-12 w-full rounded-2xl bg-[#F6B545] font-bold text-slate-950 disabled:opacity-40">{busy ? "Submitting…" : `Confirm ${action}`}</button>
      </form>
      <p className="text-sm leading-6 text-slate-600" role="status">{message}</p>
    </div>

    <section aria-live="polite">
      {status ? <div className="rounded-[30px] bg-white p-6 text-slate-950">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">Account intervention state</p><h2 className="mt-2 text-3xl font-bold">{status.status}</h2><p className="mt-2 text-sm text-slate-500">Masked phone: {status.maskedPhoneNumber ?? "Not recorded"}</p></div>
          <span className="rounded-full bg-[#FFF8EC] px-4 py-2 font-mono text-xs">{status.identityId}</span>
        </div>
        <dl className="mt-7 grid gap-4 sm:grid-cols-2">
          {[
            ["Token version", String(status.tokenVersion)],
            ["Last action", status.action ?? "None"],
            ["Requested status", status.requestedStatus ?? "None"],
            ["Provider status", status.providerStatus ?? "Not queued"],
            ["Provider attempts", String(status.providerAttemptCount)],
            ["Requested", displayDate(status.requestedAt)],
            ["Provider completed", displayDate(status.providerCompletedAt)],
            ["Changed", status.changed ? "Yes" : "No"]
          ].map(([label, value]) => <div key={label} className="rounded-2xl bg-[#FFF8EC] p-4"><dt className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</dt><dd className="mt-2 break-words text-sm font-semibold">{value}</dd></div>)}
        </dl>
        {status.providerLastError && <div className="mt-5 rounded-2xl border border-amber-300 p-4"><strong>Provider synchronization note</strong><p className="mt-2 text-sm">{status.providerLastError}</p></div>}
        {status.correlationId && <div className="mt-5 rounded-2xl border border-[#6930CA]/20 p-4"><p className="text-xs font-bold uppercase tracking-wide text-[#6930CA]">Audit correlation</p><p className="mt-2 break-all font-mono text-sm">{status.correlationId}</p></div>}
      </div> : <div className="rounded-[30px] border border-dashed border-[#cfc4d7] bg-white p-8 text-slate-600"><h2 className="text-2xl font-bold text-slate-950">No account loaded</h2><p className="mt-3 leading-7">The backend’s privacy-reduced account state must be loaded before the action form becomes available.</p></div>}
    </section>
  </div>;
}
