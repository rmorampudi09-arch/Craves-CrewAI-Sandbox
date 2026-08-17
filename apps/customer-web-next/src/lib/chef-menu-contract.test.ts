import assert from "node:assert/strict";
import test from "node:test";
import { parseChefMenuItem, parseChefMenuItemInput, parseChefMenuItems } from "./chef-menu-contract.ts";

const item = {
  id: "11111111-2222-4333-8444-555555555555",
  kitchenId: "21111111-2222-4333-8444-555555555555",
  itemName: "Meal",
  category: "Lunch",
  foodType: "VEG",
  price: 250,
  currency: "INR",
  unitPackageWeightGrams: 500,
  thermoboxRequired: false,
  available: true,
  status: "ACTIVE",
  images: [{ id: "31111111-2222-4333-8444-555555555555", menuItemId: "11111111-2222-4333-8444-555555555555", blobContainer: "private", blobName: "private", contentType: "image/jpeg", fileSizeBytes: 1000, publicUrl: "https://example.com/image.jpg", sortOrder: 0, primary: true }],
  createdAt: "2026-07-30T00:00:00Z",
  updatedAt: "2026-07-30T00:00:00Z"
};

test("allow-lists chef menu and image fields", () => {
  const parsed = parseChefMenuItem(item);
  assert.equal(parsed?.itemName, "Meal");
  assert.equal("kitchenId" in (parsed ?? {}), false);
  assert.equal("blobContainer" in (parsed?.images[0] ?? {}), false);
  assert.equal("blobName" in (parsed?.images[0] ?? {}), false);
});

test("rejects unsafe image URL and invalid values", () => {
  assert.equal(parseChefMenuItem({ ...item, images: [{ ...item.images[0], publicUrl: "http://example.com/a.jpg" }] }), null);
  assert.equal(parseChefMenuItemInput({ ...item, price: 0 }), null);
});

test("validates complete menu arrays", () => {
  assert.equal(parseChefMenuItems([item])?.length, 1);
  assert.equal(parseChefMenuItems([item, { ...item, id: "bad" }]), null);
});
