import { expect, test } from "@playwright/test";

test("public landing keeps secure headers", async ({ request }) => {
  const response = await request.get("/");

  expect(response.ok()).toBeTruthy();
  expect(response.headers()["x-content-type-options"]).toBe("nosniff");
  expect(response.headers()["x-frame-options"]).toBe("DENY");
});

test("build train route renders as a public release-planning surface", async ({ page }) => {
  await page.goto("/build-train");

  await expect(
    page.getByRole("heading", {
      name: /Production-readiness train for the canonical Craves web platform/i,
    }),
  ).toBeVisible();

  await expect(page.getByText(/Canonical Next\.js platform/i)).toBeVisible();
  await expect(page.getByText(/Admin closure in customer-web-next/i)).toBeVisible();
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
