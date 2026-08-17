import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("address BFF returns safe diagnostics without tokens or payload dumps", () => {
  const route = source("../app/api/customer/addresses/route.ts");

  assert.match(route, /correlationId/);
  assert.match(route, /upstreamStatus/);
  assert.match(route, /safeUpstreamMessage/);
  assert.doesNotMatch(route, /Authorization.*NextResponse/);
  assert.doesNotMatch(route, /console\.log\(body/);
});
