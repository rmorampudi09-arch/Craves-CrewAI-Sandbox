import { NextRequest, NextResponse } from "next/server";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

type SafeEvidence = {
  id: string;
  documentType: string;
  originalFileName: string;
  fileSizeBytes: number;
  status: string;
};

function sanitize(value: unknown): SafeEvidence | null {
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

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/chef/application?evidence=true");
    if (!upstream.ok) {
      return NextResponse.json(
        { code: upstream.status === 401 ? "SESSION_EXPIRED" : "EVIDENCE_STATUS_UNAVAILABLE" },
        { status: upstream.status },
      );
    }
    const body = await upstream.json().catch(() => null);
    if (!Array.isArray(body) || body.length > 20) {
      return NextResponse.json({ code: "INVALID_EVIDENCE_STATUS_RESPONSE" }, { status: 502 });
    }
    const items = body.map(sanitize);
    if (items.some(item => item === null)) {
      return NextResponse.json({ code: "INVALID_EVIDENCE_STATUS_RESPONSE" }, { status: 502 });
    }
    return NextResponse.json(items, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) {
      return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    }
    return NextResponse.json({ code: "EVIDENCE_STATUS_UNAVAILABLE" }, { status: 503 });
  }
}
