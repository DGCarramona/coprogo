import { expect, test } from '@playwright/test';

test.describe('Application entry', () => {
  test('redirects an unauthenticated deep link to a working sign-in page', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    // This smoke test exercises the SPA, without depending on the external Google SDK.
    await page.route('https://accounts.google.com/**', (route) => route.abort());

    await page.goto('/groups/browser-smoke/dashboard');

    await expect(page).toHaveURL('/connexion');
    await expect(page).toHaveTitle('Coprogo');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    await page.reload();

    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    expect(pageErrors).toEqual([]);
  });
});
