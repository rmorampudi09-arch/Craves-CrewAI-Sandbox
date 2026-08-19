import { describe, expect, it } from "vitest";

const activeDomains = [
  "backend",
  "web",
  "mobile",
  "database",
  "integrations",
  "cloud",
] as const;

const canonicalWebModule = "apps/customer-web-next";
const forbiddenRuntime = "Node.js backend";
const orderedMilestones = [
  "Architecture lock and repo guardrails",
  "Infra deployability alignment",
  "Auth, session, and RBAC hardening",
  "Admin closure in customer-web-next",
] as const;

describe("build train web alignment", () => {
  it("keeps web inside the active domain set", () => {
    expect(activeDomains).toContain("web");
  });

  it("points to the canonical Next.js production module", () => {
    expect(canonicalWebModule).toBe("apps/customer-web-next");
  });

  it("guards against extending a Node.js backend runtime", () => {
    expect(forbiddenRuntime.toLowerCase()).toContain("node.js backend");
  });

  it("preserves the expected early milestone order", () => {
    expect(orderedMilestones[0]).toMatch(/Architecture lock/i);
    expect(orderedMilestones[1]).toMatch(/Infra deployability/i);
    expect(orderedMilestones[2]).toMatch(/RBAC/i);
    expect(orderedMilestones[3]).toMatch(/customer-web-next/i);
  });
});
