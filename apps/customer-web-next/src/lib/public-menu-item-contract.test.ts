import assert from "node:assert/strict";
import test from "node:test";
import { parsePublicMenuItemDetail } from "./public-menu-item-contract.ts";

const item = {
  id: "11111111-2222-4333-8444-555555555555",
  kitchenId: "21111111-2222-4333-8444-555555555555",
  itemName: "Live menu item",
  description: "Prepared after ordering",
  category: "Meals",
  foodType: "VEG",
  price: 199,
  currency: "INR",
  servesCount: 1,
  preparationTimeMinutes: 25,
  spiceLevel: "MILD",
  available: true,
  status: "ACTIVE",
  images: [
    {
      publicUrl: "https://cdn.example.test/item.jpg",
      primary: true,
      sortOrder: 0,
    },
  ],
};

const kitchen = {
  id: item.kitchenId,
  identityId: "31111111-2222-4333-8444-555555555555",
  kitchenName: "Backend Kitchen",
  displayName: "Public Kitchen",
  phoneNumber: "+919999999999",
  email: "private@example.test",
  addressLine1: "Private pickup address",
  areaName: "Kondapur",
  city: "Hyderabad",
  state: "Telangana",
};

test("returns only public menu and kitchen fields", () => {
  const parsed = parsePublicMenuItemDetail(item, kitchen);
  assert.equal(parsed?.kitchenDisplayName, "Public Kitchen");
  assert.equal(parsed?.primaryImageUrl, "https://cdn.example.test/item.jpg");
  assert.equal("phoneNumber" in (parsed ?? {}), false);
  assert.equal("email" in (parsed ?? {}), false);
  assert.equal("addressLine1" in (parsed ?? {}), false);
  assert.equal("identityId" in (parsed ?? {}), false);
});

test("rejects inactive, unavailable or mismatched records", () => {
  assert.equal(parsePublicMenuItemDetail({ ...item, available: false }, kitchen), null);
  assert.equal(parsePublicMenuItemDetail({ ...item, status: "DRAFT" }, kitchen), null);
  assert.equal(
    parsePublicMenuItemDetail(item, {
      ...kitchen,
      id: "41111111-2222-4333-8444-555555555555",
    }),
    null,
  );
});

test("rejects non-https catalog images without failing the item", () => {
  const parsed = parsePublicMenuItemDetail(
    { ...item, images: [{ publicUrl: "http://unsafe.test/item.jpg", primary: true }] },
    kitchen,
  );
  assert.equal(parsed?.primaryImageUrl, null);
});
