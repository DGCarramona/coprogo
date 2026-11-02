import { expect, test as base } from '@playwright/test';

export const groupId = 'browser-smoke';
const groupPath = `/api/groups/${groupId}`;
const responses: Record<string, object> = {
  [groupPath]: { group: groupId, name: 'Appartement', members: [], pendingInvitations: [] },
  [`${groupPath}/balances`]: { group: groupId, balances: [] },
  [`${groupPath}/cash-pool/balance`]: { group: groupId, availableAmountInCents: 0 },
  [`${groupPath}/cash-pool/shares`]: { group: groupId, shares: [] },
  [`${groupPath}/cash-pool/history`]: { group: groupId, operations: [] },
  [`${groupPath}/expenses`]: [],
};

export const test = base.extend({
  page: async ({ page }, use) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('console', (message) => {
      if (message.type() === 'error') errors.push(message.text());
    });
    await page.addInitScript(() => {
      sessionStorage.setItem('coprogo.google-id-token', 'synthetic-group-test');
    });
    await page.route('https://accounts.google.com/**', (route) => route.fulfill({ body: '' }));
    await page.route('http://localhost:8080/**', (route) => {
      const path = new URL(route.request().url()).pathname;
      const json = responses[path];
      if (json === undefined) throw new Error(`Unexpected API request: ${path}`);
      return route.fulfill({ json });
    });

    await use(page);

    expect(errors).toEqual([]);
  },
});

export { expect };
