import { expect, test } from '@playwright/test';

test.describe('admin console hardening shell', () => {
  test('redirects anonymous users to sign-in messaging', async ({ page }) => {
    await page.goto('/admin');
    await expect(page.getByText(/verify administrator access|sign in with an administrator account/i)).toBeVisible();
    await expect(page.getByRole('link', { name: /administrator sign in/i })).toBeVisible();
  });

  test('chef workspace blocks unauthenticated access with secure guidance', async ({ page }) => {
    await page.goto('/chef');
    await expect(page.getByText(/secure chef access/i)).toBeVisible();
    await expect(page.getByText(/mobile otp/i)).toBeVisible();
  });
});
