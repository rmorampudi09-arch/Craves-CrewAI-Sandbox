import assert from "node:assert/strict";
import test from "node:test";
import { parseReverseGeocodedAddress } from "./location-contract.ts";

const response = {
  type: "FeatureCollection",
  features: [
    {
      type: "Feature",
      properties: {
        address: {
          countryRegion: { name: "India", ISO: "IN" },
          adminDistricts: [
            { name: "Telangana", shortName: "TS" },
            { name: "Hyderabad", shortName: "Hyderabad" },
          ],
          formattedAddress: "101, Test Road, Madhapur, Hyderabad, Telangana 500081",
          streetName: "Test Road",
          streetNumber: "101",
          locality: "Hyderabad",
          neighborhood: "Madhapur",
          postalCode: "500081",
          addressLine: "101 Test Road",
        },
        confidence: "High",
      },
    },
  ],
};

test("maps Azure Maps reverse geocoding into Craves address fields", () => {
  assert.deepEqual(parseReverseGeocodedAddress(response), {
    formattedAddress: "101, Test Road, Madhapur, Hyderabad, Telangana 500081",
    houseNumber: "101",
    street: "Test Road",
    area: "Madhapur",
    city: "Hyderabad",
    district: "Hyderabad",
    state: "Telangana",
    postalCode: "500081",
    country: "India",
    confidence: "High",
    preciseHouseNumber: true,
  });
});

test("keeps a useful address when Azure Maps cannot resolve a house number", () => {
  const sparse = structuredClone(response);
  assert.equal(Reflect.deleteProperty(sparse.features[0].properties.address, "streetNumber"), true);
  const parsed = parseReverseGeocodedAddress(sparse);
  assert.ok(parsed);
  assert.equal(parsed.houseNumber, null);
  assert.equal(parsed.preciseHouseNumber, false);
  assert.equal(parsed.area, "Madhapur");
});

test("rejects unusable provider responses", () => {
  assert.equal(parseReverseGeocodedAddress({ features: [] }), null);
  assert.equal(parseReverseGeocodedAddress(null), null);
});
