"use client";

import { CheckCircle2, CircleAlert } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { AdminChefApplication } from "@/lib/admin-chef-review-contract";

type ReviewDocument = {
  id: string;
  documentType: string;
  originalFileName: string;
  fileSizeBytes: number;
  status: string;
};

const REQUIREMENTS = [
  ["APPLICANT_PHOTO", "Applicant photograph"],
  ["GOVERNMENT_ID_FRONT", "Government photo ID — front"],
  ["GOVERNMENT_ID_BACK", "Government photo ID — back"],
  ["TAX_ID_CARD", "PAN / tax ID card"],
] as const;

function bytes(value: number): string {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function safeDocument(value: unknown): ReviewDocument | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  if (
    typeof raw.id !== "string" ||
    typeof raw.documentType !== "string" ||
    typeof raw.originalFileName !== "string" ||
    typeof raw.fileSizeBytes !== "number" ||
    typeof raw.status !== "string"
  ) return null;
  return {
    id: raw.id,
    documentType: raw.documentType,
    originalFileName: raw.originalFileName,
    fileSizeBytes: raw.fileSizeBytes,
    status: raw.status,
  };
}

export function AdminChefReviewDetails({ applicationId }: { applicationId: string }) {
  const [item, setItem] = useState<AdminChefApplication | null>(null);
  const [documents, setDocuments] = useState<ReviewDocument[]>([]);
  const [documentsAvailable, setDocumentsAvailable] = useState(false);
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("Loading chef application…");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const [response, documentResponse] = await Promise.all([
      fetch(`/api/admin/chef-reviews/${applicationId}`, { cache: "no-store" }),
      fetch(`/api/admin/chef-reviews/${applicationId}/evidence-status`, { cache: "no-store" }),
    ]);
    const body = await response.json().catch(() => null);
    if (response.status === 401) throw new Error("Administrator session expired.");
    if (response.status === 403) throw new Error("Administrator access is required.");
    if (response.status === 404) throw new Error("Chef application was not found.");
    if (!response.ok) throw new Error("Chef application is temporarily unavailable.");
    setItem(body as AdminChefApplication);

    const documentBody = await documentResponse.json().catch(() => null);
    if (documentResponse.ok && Array.isArray(documentBody)) {
      const parsed = documentBody.map(safeDocument);
      if (!parsed.some(document => document === null)) {
        setDocuments(parsed as ReviewDocument[]);
        setDocumentsAvailable(true);
        setMessage("");
        return;
      }
    }
    setDocuments([]);
    setDocumentsAvailable(false);
    setMessage("Application details loaded, but required-document status is temporarily unavailable. Approval is disabled until it can be verified.");
  }, [applicationId]);

  useEffect(() => {
    void load().catch(error => setMessage(error instanceof Error ? error.message : "Chef application is unavailable."));
  }, [load]);

  const documentByType = useMemo(() => new Map(documents.map(document => [document.documentType, document])), [documents]);
  const completedCount = REQUIREMENTS.filter(([type]) => documentByType.has(type)).length;
  const completion = completedCount * 25;
  const complete = documentsAvailable && completedCount === REQUIREMENTS.length;

  async function decide(action: "approve" | "reject") {
    if (action === "reject" && !reason.trim()) {
      setMessage("A rejection reason is required.");
      return;
    }
    if (action === "approve" && !complete) {
      setMessage("Approval is blocked until all 4 required documents are uploaded and available for review.");
      return;
    }
    if (action === "approve" && !window.confirm("Confirm that you inspected all four required documents and want to approve this Chef application.")) return;

    setBusy(true);
    setMessage("");
    try {
      const response = await fetch(`/api/admin/chef-reviews/${applicationId}/${action}`, {
        method: "POST",
        headers: action === "reject" ? { "Content-Type": "application/json" } : undefined,
        body: action === "reject" ? JSON.stringify({ reason }) : undefined,
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(action === "approve" ? "Chef approval failed. Refresh the document checklist and verify all required files." : "Chef rejection failed.");
      setItem(body as AdminChefApplication);
      setReason("");
      await load();
      setMessage(action === "approve" ? "Chef application approved." : "Chef application rejected with the review reason.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Chef decision failed.");
    } finally {
      setBusy(false);
    }
  }

  if (!item) return <section className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950"><p role="status">{message}</p></section>;

  return <div className="space-y-6">
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">{item.status}</p><h2 className="mt-2 text-3xl font-bold">{item.firstName} {item.lastName}</h2><p className="mt-2 text-sm text-slate-600">{item.email} · {item.phoneNumber}</p></div>
        <span className="text-sm text-slate-500">Submitted {new Date(item.submittedAt).toLocaleString("en-IN")}</span>
      </div>
      <p className="mt-5 text-sm leading-6">{item.addressLine1}{item.addressLine2 ? `, ${item.addressLine2}` : ""}{item.landmark ? `, ${item.landmark}` : ""}<br />{item.city}, {item.state} {item.postalCode ?? ""}</p>
      {item.rejectionReason && <div className="mt-5 rounded-2xl bg-red-50 p-4 text-sm text-red-900"><strong>Rejection reason</strong><p className="mt-2">{item.rejectionReason}</p></div>}
    </section>

    <section className="rounded-[30px] bg-white p-6 text-slate-950 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">Document review</p><h2 className="mt-1 text-2xl font-bold">Required application documents</h2><p className="mt-2 text-sm text-slate-600">Open every uploaded file through the administrator-only content stream before approving.</p></div>
        <div className="min-w-[180px] rounded-2xl bg-[#FFF8EC] p-4"><div className="flex justify-between gap-3 text-sm"><strong>{completedCount}/4 ready</strong><span>{completion}%</span></div><div className="mt-2 h-2.5 overflow-hidden rounded-full bg-slate-200"><div className="h-full rounded-full bg-[#6930CA] transition-[width] duration-500" style={{ width: `${completion}%` }} /></div></div>
      </div>

      <div className="mt-5 grid gap-3 lg:grid-cols-2">
        {REQUIREMENTS.map(([type, label]) => {
          const document = documentByType.get(type);
          return <article key={type} className={`rounded-2xl border p-4 ${document ? "border-emerald-200 bg-emerald-50/50" : "border-amber-200 bg-amber-50"}`}>
            <div className="flex items-start justify-between gap-3"><div className="flex gap-3">{document ? <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-700" /> : <CircleAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" />}<div><strong>{label}</strong>{document ? <p className="mt-1 text-sm text-slate-600">{document.originalFileName} · {bytes(document.fileSizeBytes)} · {document.status}</p> : <p className="mt-1 text-sm text-amber-800">Missing — Chef must upload this item.</p>}</div></div><span className={`rounded-full px-3 py-1 text-xs font-bold ${document ? "bg-white text-emerald-800" : "bg-white text-amber-800"}`}>{document ? "READY ✓" : "MISSING"}</span></div>
            {document && <a target="_blank" rel="noopener noreferrer" href={`/api/admin/chef-reviews/${item.id}/documents/${document.id}/content`} className="mt-4 inline-flex rounded-2xl bg-[#6930CA] px-4 py-2 text-sm font-bold text-white">Open document</a>}
          </article>;
        })}
      </div>

      {!documentsAvailable && <p className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">Document metadata could not be verified. Approval is fail-closed until this check succeeds.</p>}
      {complete && <p className="mt-4 rounded-2xl bg-emerald-50 p-4 text-sm font-semibold text-emerald-900">4/4 required documents are available for inspection. Admin may approve after reviewing their contents.</p>}
    </section>

    {item.status === "PENDING" && <section className="rounded-[30px] bg-white p-6 text-slate-950 sm:p-8">
      <h2 className="text-2xl font-bold">Decision</h2>
      <p className="mt-2 text-sm text-slate-600">Approval unlocks only when the required-document checklist is 4/4. Rejection remains available with a reason.</p>
      <label className="mt-5 block text-sm font-bold">Rejection reason<textarea value={reason} maxLength={1000} onChange={event => setReason(event.target.value)} className="mt-2 min-h-28 w-full rounded-2xl bg-[#FFF8EC] p-4" /></label>
      <div className="mt-5 flex flex-wrap gap-3"><button disabled={busy || !complete} onClick={() => void decide("approve")} className="rounded-2xl bg-green-700 px-5 py-3 font-bold text-white disabled:cursor-not-allowed disabled:opacity-40">Approve</button><button disabled={busy || !reason.trim()} onClick={() => void decide("reject")} className="rounded-2xl bg-red-700 px-5 py-3 font-bold text-white disabled:opacity-50">Reject</button></div>
    </section>}

    {message && <p className="rounded-2xl bg-[#FFF8EC] p-4 text-slate-950" role="status">{message}</p>}
  </div>;
}
