import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("customer web exposes the exact running Git commit without caching", () => {
  const route = source("../app/api/version/route.ts");
  assert.match(route, /process\.env\.CRAVES_BUILD_SHA/);
  assert.match(route, /\^\[0-9a-f\]\{40\}\$/i);
  assert.match(route, /Cache-Control/);
  assert.match(route, /no-store/);
});

test("deployment shifts Container Apps traffic and verifies the public commit", () => {
  const pipeline = source(
    "../../../../azure-pipelines-customer-web-next-delivery-tracking.yml",
  );

  assert.match(pipeline, /activeRevisionsMode/);
  assert.match(pipeline, /az containerapp ingress traffic set/);
  assert.match(pipeline, /--revision-weight "\$NEW_REVISION=100"/);
  assert.match(pipeline, /CRAVES_BUILD_SHA="\$EXPECTED_SHA"/);
  assert.match(pipeline, /\/api\/version\?release=\$EXPECTED_SHA/);
  assert.match(pipeline, /"\$LIVE_SHA" == "\$EXPECTED_SHA"/);
  assert.match(pipeline, /Secure Craves access/);
  assert.match(pipeline, /ROLLBACK_TRAFFIC_B64/);
});
