import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminChefApplication } from "./admin-chef-review-contract.ts";

const application = {
  id: "11111111-1111-4111-8111-111111111111",
  identityId: "22222222-2222-4222-8222-222222222222",
  phoneNumber: "+919999999999",
  email: "chef@example.com",
  firstName: "Home",
  lastName: "Chef",
  addressLine1: "1 Test Road",
  addressLine2: null,
  landmark: null,
  city: "Hyderabad",
  state: "Telangana",
  postalCode: "500001",
  latitude: 17.4,
  longitude: 78.4,
  status: "PENDING",
  rejectionReason: null,
  submittedAt: "2026-07-30T00:00:00Z",
  reviewedAt: null,
  reviewedByIdentityId: null,
  documents: [{
    id: "33333333-3333-4333-8333-333333333333",
    documentType: "PAN_CARD",
    originalFileName: "pan.pdf",
    blobContainer: "private",
    blobName: "secret/path",
    contentType: "application/pdf",
    fileSizeBytes: 1024,
    status: "UPLOADED",
    createdAt: "2026-07-30T00:00:00Z",
    updatedAt: "2026-07-30T00:00:00Z"
  }]
};

test("admin review strips identity and blob storage fields", () => {
  const parsed = parseAdminChefApplication(application);
  assert.equal(parsed?.email, "chef@example.com");
  assert.equal("identityId" in (parsed ?? {}), false);
  assert.equal("reviewedByIdentityId" in (parsed ?? {}), false);
  assert.equal("blobContainer" in (parsed?.documents[0] ?? {}), false);
  assert.equal("blobName" in (parsed?.documents[0] ?? {}), false);
});

test("rejects unsupported document content type", () => {
  assert.equal(parseAdminChefApplication({ ...application, documents: [{ ...application.documents[0], contentType: "text/html" }] }), null);
});
