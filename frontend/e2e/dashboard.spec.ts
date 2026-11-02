import { expect, groupId, test } from './fixtures/group-page.js';

test.describe('Group dashboard rendering', () => {
  test('shows a load error for missing balances and renders translated widgets after retry', async ({
    page,
  }) => {
    const balancesUrl = `http://localhost:8080/api/groups/${groupId}/balances`;
    await page.route(balancesUrl, (route) => route.fulfill({ json: { group: groupId } }));

    await page.goto('/groups/browser-smoke/dashboard');
    await expect(page).toHaveTitle('Coprogo');
    await expect(page.getByRole('button', { name: 'Réessayer', exact: true })).toBeVisible();
    await expect(page.locator('app-cash-pool-income-widget')).toHaveCount(0);
    await expect(page).toHaveURL('/groups/browser-smoke/dashboard');

    await page.unroute(balancesUrl);
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
  });
});
