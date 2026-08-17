"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ChefApplicationEvidenceUploader,
  type ChefEvidenceMetadata,
} from "@/components/chef-application-evidence-uploader";

function safeMetadata(value: unknown): ChefEvidenceMetadata | null {
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

export function ChefApplicationDocumentPanel() {
  const [ready, setReady] = useState(false);
  const [locked, setLocked] = useState(false);
  const [documents, setDocuments] = useState<ChefEvidenceMetadata[]>([]);
  const [version, setVersion] = useState(0);

  const load = useCallback(async () => {
    const applicationResponse = await fetch("/api/chef/application", { cache: "no-store" });
    const application = await applicationResponse.json().catch(() => null) as { id?: unknown; status?: unknown } | null;
    if (!applicationResponse.ok) return;

    const applicationReady = typeof application?.id === "string" && application.id.length > 0;
    setReady(applicationReady);
    setLocked(application?.status === "APPROVED");
    if (!applicationReady) return;

    const documentResponse = await fetch("/api/chef/application/evidence-status", { cache: "no-store" });
    const body = await documentResponse.json().catch(() => null);
    if (!documentResponse.ok || !Array.isArray(body)) return;
    const parsed = body.map(safeMetadata);
    if (parsed.some(item => item === null)) return;
    setDocuments(parsed as ChefEvidenceMetadata[]);
    setVersion(current => current + 1);
  }, []);

  useEffect(() => {
    void load();
    if (ready) return;
    const timer = window.setInterval(() => void load(), 2_500);
    return () => window.clearInterval(timer);
  }, [load, ready]);

  return (
    <ChefApplicationEvidenceUploader
      key={`${ready}-${locked}-${version}`}
      applicationReady={ready}
      locked={locked}
      initialDocuments={documents}
    />
  );
}
