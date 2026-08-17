"use client";

import { CheckCircle2, CircleAlert, FileUp, ShieldCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useRef, useState } from "react";

export type ChefEvidenceMetadata = {
  id: string;
  documentType: string;
  originalFileName: string;
  fileSizeBytes: number;
  status: string;
};

type EvidenceType =
  | "APPLICANT_PHOTO"
  | "GOVERNMENT_ID_FRONT"
  | "GOVERNMENT_ID_BACK"
  | "TAX_ID_CARD";

type ProgressState = {
  progress: number;
  phase: "IDLE" | "UPLOADING" | "SECURING" | "DONE" | "ERROR";
  message: string;
};

const REQUIREMENTS: Array<{
  type: EvidenceType;
  title: string;
  helper: string;
  accept: string;
}> = [
  {
    type: "APPLICANT_PHOTO",
    title: "Applicant photograph",
    helper: "Upload a recent passport-size or clear passport-style portrait. JPG or PNG only.",
    accept: "image/jpeg,image/png",
  },
  {
    type: "GOVERNMENT_ID_FRONT",
    title: "Aadhaar / government photo ID — front",
    helper: "Upload the front side of the government photo ID used for the application. A masked Aadhaar copy is preferred when suitable.",
    accept: "application/pdf,image/jpeg,image/png",
  },
  {
    type: "GOVERNMENT_ID_BACK",
    title: "Aadhaar / government photo ID — back",
    helper: "Upload the reverse side showing the address/details required for the application.",
    accept: "application/pdf,image/jpeg,image/png",
  },
  {
    type: "TAX_ID_CARD",
    title: "PAN card",
    helper: "Upload the applicant's PAN/tax-ID card as PDF, JPG or PNG.",
    accept: "application/pdf,image/jpeg,image/png",
  },
];

const INITIAL_PROGRESS: ProgressState = { progress: 0, phase: "IDLE", message: "" };

function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function parseUploadResponse(value: unknown): ChefEvidenceMetadata | null {
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

export function ChefApplicationEvidenceUploader({
  applicationReady,
  locked,
  initialDocuments,
}: {
  applicationReady: boolean;
  locked: boolean;
  initialDocuments: ChefEvidenceMetadata[];
}) {
  const router = useRouter();
  const [documents, setDocuments] = useState<ChefEvidenceMetadata[]>(initialDocuments);
  const [files, setFiles] = useState<Partial<Record<EvidenceType, File>>>({});
  const [progress, setProgress] = useState<Partial<Record<EvidenceType, ProgressState>>>({});
  const requestRefs = useRef<Partial<Record<EvidenceType, XMLHttpRequest>>>({});

  const uploadedByType = useMemo(
    () => new Map(documents.map(document => [document.documentType, document])),
    [documents],
  );
  const completedCount = REQUIREMENTS.filter(item => uploadedByType.has(item.type)).length;
  const overallProgress = Math.round((completedCount / REQUIREMENTS.length) * 100);

  function stateFor(type: EvidenceType): ProgressState {
    return progress[type] ?? INITIAL_PROGRESS;
  }

  function setTypeProgress(type: EvidenceType, next: ProgressState) {
    setProgress(current => ({ ...current, [type]: next }));
  }

  function chooseFile(type: EvidenceType, file: File | null) {
    setFiles(current => {
      const next = { ...current };
      if (file) next[type] = file;
      else delete next[type];
      return next;
    });
    setTypeProgress(type, INITIAL_PROGRESS);
  }

  function upload(type: EvidenceType) {
    const file = files[type];
    if (!file) {
      setTypeProgress(type, { progress: 0, phase: "ERROR", message: "Choose a file first." });
      return;
    }
    if (!applicationReady || locked) return;

    requestRefs.current[type]?.abort();
    const data = new FormData();
    data.set("documentType", type);
    data.set("file", file);

    const xhr = new XMLHttpRequest();
    requestRefs.current[type] = xhr;
    xhr.open("POST", "/api/chef/application/proof-files", true);
    xhr.responseType = "json";
    xhr.withCredentials = true;

    setTypeProgress(type, { progress: 0, phase: "UPLOADING", message: "Starting secure upload…" });

    xhr.upload.onprogress = event => {
      if (!event.lengthComputable) return;
      const value = Math.min(100, Math.round((event.loaded / event.total) * 100));
      setTypeProgress(type, {
        progress: value,
        phase: value >= 100 ? "SECURING" : "UPLOADING",
        message: value >= 100 ? "100% transferred · securing document…" : `Uploading · ${value}%`,
      });
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        const uploaded = parseUploadResponse(xhr.response);
        if (!uploaded) {
          setTypeProgress(type, { progress: 100, phase: "ERROR", message: "Upload completed but the response could not be verified." });
          return;
        }
        setDocuments(current => [
          ...current.filter(document => document.documentType !== type),
          uploaded,
        ]);
        setFiles(current => {
          const next = { ...current };
          delete next[type];
          return next;
        });
        setTypeProgress(type, { progress: 100, phase: "DONE", message: "Uploaded securely ✓" });
        router.refresh();
        return;
      }
      const body = xhr.response as { message?: unknown } | null;
      setTypeProgress(type, {
        progress: 0,
        phase: "ERROR",
        message: typeof body?.message === "string" ? body.message : "Upload failed. Check the file and try again.",
      });
    };

    xhr.onerror = () => setTypeProgress(type, { progress: 0, phase: "ERROR", message: "Network error during upload. Try again." });
    xhr.onabort = () => setTypeProgress(type, { progress: 0, phase: "IDLE", message: "Upload cancelled." });
    xhr.send(data);
  }

  return (
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="max-w-3xl">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">FSSAI application documents</p>
          <h2 className="mt-2 text-2xl font-bold">Upload all 4 required documents</h2>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            Each document has its own secure upload. You can replace a file while the Chef application is pending. Identity files are not exposed through public Blob URLs.
          </p>
        </div>
        <div className="min-w-[180px] rounded-2xl bg-white p-4">
          <div className="flex items-center justify-between gap-3 text-sm"><strong>{completedCount}/4 uploaded</strong><span>{overallProgress}%</span></div>
          <div className="mt-2 h-2.5 overflow-hidden rounded-full bg-slate-200" aria-label={`Overall document completion ${overallProgress}%`}>
            <div className="h-full rounded-full bg-[#6930CA] transition-[width] duration-500 ease-out" style={{ width: `${overallProgress}%` }} />
          </div>
          <p className="mt-2 text-xs text-slate-500">Admin approval requires 4/4.</p>
        </div>
      </div>

      {!applicationReady && (
        <div className="mt-5 flex gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
          <CircleAlert className="mt-0.5 h-5 w-5 shrink-0" />
          <div><strong>Submit your Chef details first.</strong><p className="mt-1">After the application record is created, all four upload buttons become available.</p></div>
        </div>
      )}

      {locked && (
        <div className="mt-5 flex gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0" />
          <div><strong>Application approved.</strong><p className="mt-1">Evidence is locked and cannot be replaced.</p></div>
        </div>
      )}

      <div className="mt-6 space-y-4">
        {REQUIREMENTS.map(requirement => {
          const uploaded = uploadedByType.get(requirement.type);
          const selected = files[requirement.type];
          const uploadState = stateFor(requirement.type);
          const busy = uploadState.phase === "UPLOADING" || uploadState.phase === "SECURING";
          const displayProgress = uploadState.phase === "IDLE" && uploaded ? 100 : uploadState.progress;

          return (
            <article key={requirement.type} className="rounded-2xl border border-[#eadfd0] bg-white p-4 sm:p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex min-w-0 gap-3">
                  <div className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${uploaded ? "bg-emerald-50 text-emerald-700" : "bg-[#f3ecff] text-[#6930CA]"}`}>
                    {uploaded ? <CheckCircle2 className="h-5 w-5" /> : <FileUp className="h-5 w-5" />}
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-bold">{requirement.title} <span className="text-red-600">*</span></h3>
                    <p className="mt-1 text-xs leading-5 text-slate-500">{requirement.helper}</p>
                    {uploaded && <p className="mt-2 truncate text-xs font-semibold text-emerald-700">Uploaded: {uploaded.originalFileName} · {formatBytes(uploaded.fileSizeBytes)}</p>}
                  </div>
                </div>
                <span className={`rounded-full px-3 py-1 text-xs font-bold ${uploaded ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>
                  {uploaded ? "Uploaded ✓" : "Required"}
                </span>
              </div>

              <div className="mt-4 grid gap-3 md:grid-cols-[1fr_auto] md:items-end">
                <label className="text-sm font-semibold">
                  {uploaded ? "Choose replacement file" : "Choose file"}
                  <input
                    type="file"
                    accept={requirement.accept}
                    disabled={!applicationReady || locked || busy}
                    onChange={event => chooseFile(requirement.type, event.target.files?.[0] ?? null)}
                    className="mt-2 block w-full rounded-2xl border border-slate-300 bg-white px-4 py-3 text-sm disabled:bg-slate-100"
                  />
                  {selected && <span className="mt-1 block text-xs font-normal text-slate-500">Selected: {selected.name} · {formatBytes(selected.size)}</span>}
                </label>
                <button
                  type="button"
                  disabled={!applicationReady || locked || busy || !selected}
                  onClick={() => upload(requirement.type)}
                  className="min-h-12 rounded-full bg-[#6930CA] px-6 font-bold text-white disabled:opacity-40"
                >
                  {busy ? "Uploading…" : uploaded ? "Replace" : "Upload"}
                </button>
              </div>

              {(busy || uploadState.phase === "DONE" || uploadState.phase === "ERROR" || uploaded) && (
                <div className="mt-4" aria-live="polite">
                  <div className="flex items-center justify-between gap-3 text-xs"><span className={uploadState.phase === "ERROR" ? "font-semibold text-red-700" : "text-slate-600"}>{uploadState.message || (uploaded ? "Stored securely" : "")}</span><strong>{displayProgress}%</strong></div>
                  <div className="mt-2 h-2.5 overflow-hidden rounded-full bg-slate-200">
                    <div
                      className={`h-full rounded-full transition-[width] duration-300 ease-out ${uploadState.phase === "ERROR" ? "bg-red-500" : uploaded || uploadState.phase === "DONE" ? "bg-emerald-600" : "bg-[#6930CA]"}`}
                      style={{ width: `${displayProgress}%` }}
                    />
                  </div>
                </div>
              )}
            </article>
          );
        })}
      </div>

      <div className={`mt-5 rounded-2xl p-4 text-sm ${completedCount === 4 ? "bg-emerald-50 text-emerald-900" : "bg-white text-slate-700"}`}>
        {completedCount === 4
          ? "All required FSSAI application evidence is uploaded. Craves Admin can now complete document review."
          : `${4 - completedCount} required document${4 - completedCount === 1 ? "" : "s"} remaining before Admin can approve the Chef application.`}
      </div>
    </section>
  );
}
