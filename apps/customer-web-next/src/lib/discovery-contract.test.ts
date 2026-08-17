import assert from "node:assert/strict";
import test from "node:test";
import { parseKitchenDiscovery, parseMenuDiscovery } from "./discovery-contract.ts";

const page = { page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false };

test("parses safe nearby kitchen response and drops private fields", () => {
  const parsed = parseKitchenDiscovery({
    latitude: 17.4,
    longitude: 78.4,
    radiusMeters: 5000,
    page,
    kitchens: [{
      id: "11111111-1111-4111-8111-111111111111",
      kitchenName: "Annapurna Home Kitchen",
      displayName: "Annapurna",
      description: "Homestyle meals",
      areaName: "Kukatpally",
      city: "Hyderabad",
      state: "Telangana",
      latitude: 17.41,
      longitude: 78.42,
      distanceMeters: 850,
      activeMenuItemCount: 8,
      phoneNumber: "+919999999999",
      email: "chef@example.com"
    }]
  });
  assert.ok(parsed);
  assert.equal(parsed.kitchens[0]?.kitchenName, "Annapurna Home Kitchen");
  assert.equal("phoneNumber" in parsed.kitchens[0]!, false);
  assert.equal("email" in parsed.kitchens[0]!, false);
  assert.equal("latitude" in parsed.kitchens[0]!, false);
});

test("rejects insecure menu image URL", () => {
  const parsed = parseMenuDiscovery({
    latitude: 17.4,
    longitude: 78.4,
    radiusMeters: 5000,
    page,
    menuItems: [{
      id: "22222222-2222-4222-8222-222222222222",
      kitchenId: "11111111-1111-4111-8111-111111111111",
      kitchenName: "Annapurna Home Kitchen",
      itemName: "Andhra Meals",
      category: "Lunch",
      foodType: "VEG",
      city: "Hyderabad",
      state: "Telangana",
      price: 180,
      currency: "INR",
      distanceMeters: 850,
      primaryImageUrl: "http://unsafe.example/image.jpg"
    }]
  });
  assert.ok(parsed);
  assert.equal(parsed.menuItems[0]?.primaryImageUrl, null);
});

test("rejects malformed coordinates and identifiers", () => {
  assert.equal(parseKitchenDiscovery({ latitude: 100, longitude: 78, radiusMeters: 5000, page, kitchens: [] }), null);
  assert.equal(parseKitchenDiscovery({
    latitude: 17,
    longitude: 78,
    radiusMeters: 5000,
    page,
    kitchens: [{ id: "not-a-uuid", kitchenName: "Kitchen", city: "Hyderabad", state: "Telangana", distanceMeters: 10, activeMenuItemCount: 1 }]
  }), null);
});
