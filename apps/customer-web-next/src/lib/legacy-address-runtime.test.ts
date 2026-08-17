import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("address management explains and repairs incomplete historical rows", () => {
  const screen = source("../screens/Profile/Addresses.tsx");

  assert.match(screen, /UPDATE REQUIRED/);
  assert.match(screen, /parseAddressInput/);
  assert.match(screen, /address\.recipientName \?\? ""/);
  assert.match(screen, /address\.latitude == null \? ""/);
  assert.doesNotMatch(screen, /address\.latitude \?\? 0/);
});
