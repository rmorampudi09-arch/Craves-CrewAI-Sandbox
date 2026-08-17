import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export function GET() {
  const configured = process.env.CRAVES_BUILD_SHA?.trim() ?? "";
  const commitSha = /^[0-9a-f]{40}$/i.test(configured) ? configured : "unknown";

  return NextResponse.json(
    { commitSha },
    {
      headers: {
        "Cache-Control": "no-store, no-cache, max-age=0, must-revalidate",
      },
    },
  );
}
