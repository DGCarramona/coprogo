import { expect, test } from '@playwright/test';

test.describe('Group dashboard rendering', () => {
  test('shows a load error for missing balances and renders translated widgets after retry', async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('console', (message) => {
      if (message.type() === 'error') errors.push(message.text());
    });
    await page.addInitScript(() => {
      sessionStorage.setItem('coprogo.google-id-token', 'synthetic-dashboard-test');
    });
    await page.route('https://accounts.google.com/**', (route) => route.fulfill({ body: '' }));

    let incompleteBalances = true;
    await page.route('http://localhost:8080/**', async (route) => {
      const headers = {
        'access-control-allow-origin': '*',
        'access-control-allow-headers': '*',
        'access-control-allow-methods': '*',
      };
      if (route.request().method() === 'OPTIONS') {
        return route.fulfill({ status: 204, headers });
      }
      const path = new URL(route.request().url()).pathname;
      const group = 'browser-smoke';
      const responses: Record<string, object> = {
        [`/api/groups/${group}/balances`]: incompleteBalances ? { group } : { group, balances: [] },
        [`/api/groups/${group}/cash-pool/balance`]: { group, availableAmountInCents: 0 },
        [`/api/groups/${group}/cash-pool/shares`]: { group, shares: [] },
        [`/api/groups/${group}/cash-pool/history`]: { group, operations: [] },
        [`/api/groups/${group}/expenses`]: [],
        [`/api/groups/${group}`]: {
          group,
          name: 'Appartement',
          members: [],
          pendingInvitations: [],
        },
      };
      const response = responses[path];
      if (response === undefined) throw new Error(`Unexpected API request: ${path}`);
      return route.fulfill({ headers, json: response });
    });

    await page.goto('/groups/browser-smoke/dashboard');
    await expect(page).toHaveTitle('Coprogo');
    await expect(page.getByRole('button', { name: 'Réessayer', exact: true })).toBeVisible();
    await expect(page.locator('app-cash-pool-income-widget')).toHaveCount(0);
    await expect(page).toHaveURL('/groups/browser-smoke/dashboard');

    incompleteBalances = false;
    await page.getByRole('button', { name: 'Réessayer', exact: true }).click();
    await expect(page.getByText('Enregistrer un revenu', { exact: true })).toBeVisible();
    await expect(page.getByLabel('Montant du revenu en euros')).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Enregistrer le revenu', exact: true }),
    ).toBeVisible();
    await expect(page.getByText('Aucune opération de caisse commune enregistrée.')).toBeVisible();
    await page.getByLabel('Montant du revenu en euros').fill('120');
    await page.getByLabel('Date de réception', { exact: true }).fill('2026-11-02T12:00');
    await expect(
      page.getByRole('button', { name: 'Enregistrer le revenu', exact: true }),
    ).toBeEnabled();

    await page.reload();
    await expect(page.getByLabel('Montant du revenu en euros')).toBeVisible();
    await expect(page).toHaveURL('/groups/browser-smoke/dashboard');
    expect(errors).toEqual([]);
  });
});
