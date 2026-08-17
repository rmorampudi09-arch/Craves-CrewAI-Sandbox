import { isSameOrigin } from "@/lib/request-security";
import { NextRequest, NextResponse } from "next/server";
import { parseChefProofDocument } from "@/lib/chef-application-contract";

export const dynamic = "force-dynamic";

const ALLOWED_TYPES = new Set([
  "APPLICANT_PHOTO",
  "GOVERNMENT_ID_FRONT",
  "GOVERNMENT_ID_BACK",
  "TAX_ID_CARD",
]);
const ALLOWED_CONTENT_TYPES = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
]);
const PHOTO_CONTENT_TYPES = new Set(["image/jpeg", "image/png"]);
const MAX_FILE_BYTES = 10_000_000;

function apiBaseUrl(): string {
  const value = process.env.CRAVES_API_BASE_URL?.trim();
  if (!value?.startsWith("https://"))
    throw new Error("CRAVES_API_BASE_URL must use HTTPS");
  return value.replace(/\/$/, "");
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request))
    return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token)
    return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });

  const form = await request.formData().catch(() => null);
  const documentType = form?.get("documentType");
  const file = form?.get("file");
  if (typeof documentType !== "string" || !ALLOWED_TYPES.has(documentType) || !(file instanceof File)) {
    return NextResponse.json({ code: "INVALID_PROOF_FILE_REQUEST" }, { status: 400 });
  }
  if (!ALLOWED_CONTENT_TYPES.has(file.type) || file.size < 1 || file.size > MAX_FILE_BYTES) {
    return NextResponse.json({ code: "INVALID_PROOF_FILE" }, { status: 400 });
  }
  if (documentType === "APPLICANT_PHOTO" && !PHOTO_CONTENT_TYPES.has(file.type)) {
    return NextResponse.json({ code: "INVALID_APPLICANT_PHOTO" }, { status: 400 });
  }

  const upstreamForm = new FormData();
  upstreamForm.set("file", file, file.name);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30_000);
  try {
    const upstream = await fetch(
      `${apiBaseUrl()}/chef/application/proof-files?documentType=${encodeURIComponent(documentType)}`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
        body: upstreamForm,
        cache: "no-store",
        signal: controller.signal,
      },
    );
    if (!upstream.ok) {
      const response = NextResponse.json(
        {
          code: upstream.status === 401 ? "SESSION_EXPIRED" : "PROOF_FILE_UPLOAD_FAILED",
          message: upstream.status === 401
            ? "Your session expired. Sign in again."
            : upstream.status === 400
              ? "The file was rejected. Use the requested JPG, PNG or PDF format under 10 MB."
              : upstream.status === 409
                ? "Proof files cannot be changed after chef approval."
                : "Proof upload is temporarily unavailable.",
        },
        { status: upstream.status },
      );
      if (upstream.status === 401) response.cookies.delete("craves_access_token");
      return response;
    }
    const document = parseChefProofDocument(await upstream.json().catch(() => null));
    if (!document) return NextResponse.json({ code: "INVALID_PROOF_FILE_RESPONSE" }, { status: 502 });
    const response = NextResponse.json(document);
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      { code: timedOut ? "PROOF_FILE_TIMEOUT" : "PROOF_FILE_UNAVAILABLE" },
      { status: timedOut ? 504 : 503 },
    );
  } finally {
    clearTimeout(timeout);
  }
}
