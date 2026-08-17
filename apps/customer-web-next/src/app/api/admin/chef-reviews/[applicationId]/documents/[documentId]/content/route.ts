import { NextRequest, NextResponse } from "next/server";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

const CONTENT_TYPES = new Set(["application/pdf", "image/jpeg", "image/png"]);

export async function GET(request: NextRequest, context: { params: Promise<{ applicationId: string; documentId: string }> }) {
  const { applicationId, documentId } = await context.params;
  if (!isUuid(applicationId) || !isUuid(documentId)) return NextResponse.json({ code: "INVALID_DOCUMENT_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/backoffice/chef-reviews/${applicationId}/documents/${documentId}/content`, {}, 15_000);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : upstream.status === 404 ? "CHEF_DOCUMENT_NOT_FOUND" : "CHEF_DOCUMENT_UNAVAILABLE" }, { status: upstream.status });
    const contentType = upstream.headers.get("content-type")?.split(";")[0]?.trim() ?? "";
    const length = Number(upstream.headers.get("content-length") ?? "0");
    if (!CONTENT_TYPES.has(contentType) || !Number.isSafeInteger(length) || length < 1 || length > 10_000_000) return NextResponse.json({ code: "INVALID_CHEF_DOCUMENT_RESPONSE" }, { status: 502 });
    const bytes = await upstream.arrayBuffer();
    if (bytes.byteLength !== length) return NextResponse.json({ code: "INVALID_CHEF_DOCUMENT_RESPONSE" }, { status: 502 });
    const response = new NextResponse(bytes, { status: 200, headers: { "Content-Type": contentType, "Content-Length": String(length), "Cache-Control": "no-store, no-cache, must-revalidate", "Content-Security-Policy": "sandbox", "X-Content-Type-Options": "nosniff" } });
    const disposition = upstream.headers.get("content-disposition");
    if (disposition && disposition.length <= 500 && !/[\r\n]/.test(disposition)) response.headers.set("Content-Disposition", disposition);
    return response;
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "CHEF_DOCUMENT_UNAVAILABLE" }, { status: 503 });
  }
}
