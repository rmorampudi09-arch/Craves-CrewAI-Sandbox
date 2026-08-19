import { test, expect } from "@playwright/test";

test("public landing keeps secure headers", async ({ request }) => {
  const response = await request.get("/");
  expect(response.ok()).toBeTruthy();
  expect(response.headers()["x-content-type-options"]).toBe("nosniff");
  expect(response.headers()["x-frame-options"]).toBe("DENY");
});

test("admin route stays non-cacheable", async ({ request }) => {
  const response = await request.get("/admin", {
    failOnStatusCode: false,
  });
  expect(response.headers()["cache-control"]).toContain("no-store");
});

test("chef route stays non-cacheable", async ({ request }) => {
  const response = await request.get("/chef", {
    failOnStatusCode: false,
  });
  expect(response.headers()["cache-control"]).toContain("no-store");
});
