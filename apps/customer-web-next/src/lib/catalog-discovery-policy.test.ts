import assert from "node:assert/strict";
import test from "node:test";
import {
  candidateDiscoveryRadii,
  formatDiscoveryRadius,
} from "./catalog-discovery-policy.ts";

test("expands normal discovery from 5 km to 15 km and then 50 km", () => {
  assert.deepEqual(candidateDiscoveryRadii(), [5_000, 15_000, 50_000]);
});

test("preserves a custom starting radius and never searches backwards", () => {
  assert.deepEqual(candidateDiscoveryRadii(10_000), [10_000, 15_000, 50_000]);
  assert.deepEqual(candidateDiscoveryRadii(20_000), [20_000, 50_000]);
});

test("rejects radii beyond the deployed Catalog query limit", () => {
  assert.throws(() => candidateDiscoveryRadii(0));
  assert.throws(() => candidateDiscoveryRadii(50_001));
  assert.equal(formatDiscoveryRadius(15_000), "15 km");
});
