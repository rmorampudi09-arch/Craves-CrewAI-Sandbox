import assert from "node:assert/strict";
import test from "node:test";
import { parseChefApplication, parseChefApplicationInput, parseChefProofDocument } from "./chef-application-contract.ts";

const application = {
  id: "11111111-2222-4333-8444-555555555555",
  identityId: "21111111-2222-4333-8444-555555555555",
  phoneNumber: "+919999999999",
  email: "chef@example.com",
  firstName: "Chef",
  lastName: "One",
  addressLine1: "1 Test Road",
  city: "Hyderabad",
  state: "Telangana",
  status: "PENDING",
  reviewedByIdentityId: "31111111-2222-4333-8444-555555555555",
  documents: [{
    id: "41111111-2222-4333-8444-555555555555",
    documentType: "PAN_CARD",
    originalFileName: "proof.pdf",
    blobContainer: "private",
    blobName: "private",
    contentType: "application/pdf",
    fileSizeBytes: 1024,
    status: "UPLOADED",
    createdAt: "2026-07-30T00:00:00Z"
  }]
};

test("allow-lists chef application and document metadata", () => {
  const parsed = parseChefApplication(application);
  assert.equal(parsed?.status, "PENDING");
  assert.equal("identityId" in (parsed ?? {}), false);
  assert.equal("phoneNumber" in (parsed ?? {}), false);
  assert.equal("reviewedByIdentityId" in (parsed ?? {}), false);
  assert.equal("blobContainer" in (parsed?.documents[0] ?? {}), false);
  assert.equal("blobName" in (parsed?.documents[0] ?? {}), false);
});

test("accepts current Chef application evidence types", () => {
  for (const documentType of [
    "APPLICANT_PHOTO",
    "GOVERNMENT_ID_FRONT",
    "GOVERNMENT_ID_BACK",
    "TAX_ID_CARD",
  ]) {
    assert.ok(parseChefProofDocument({ ...application.documents[0], documentType }));
  }
});

test("rejects unsupported document metadata", () => {
  assert.equal(parseChefProofDocument({ ...application.documents[0], documentType: "UNKNOWN" }), null);
  assert.equal(parseChefProofDocument({ ...application.documents[0], fileSizeBytes: 50_000_000 }), null);
});

test("validates paired coordinates and email", () => {
  const valid = { email: "chef@example.com", firstName: "Chef", lastName: "One", addressLine1: "Road", city: "Hyderabad", state: "Telangana", latitude: 17.4, longitude: 78.4 };
  assert.ok(parseChefApplicationInput(valid));
  assert.equal(parseChefApplicationInput({ ...valid, email: "bad" }), null);
  assert.equal(parseChefApplicationInput({ ...valid, longitude: null }), null);
});
