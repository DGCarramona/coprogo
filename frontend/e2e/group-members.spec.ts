import { expect, groupId, test } from './fixtures/group-page.js';

test('adds a member directly and refreshes the group member list', async ({ page }) => {
  let members = [
    { member: 'alice@example.com', joinedAt: '2026-01-01T00:00:00Z', addedBy: 'alice@example.com' },
  ];
  await page.route(`http://localhost:8080/api/groups/${groupId}`, (route) =>
    route.fulfill({
      json: {
        group: groupId,
        name: 'Appartement',
        members,
      },
    }),
  );
  await page.route(`http://localhost:8080/api/groups/${groupId}/members`, (route) => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({ member: 'frank@example.com' });
    members = [
      ...members,
      {
        member: 'frank@example.com',
        joinedAt: '2026-01-03T00:00:00Z',
        addedBy: 'alice@example.com',
      },
    ];
    return route.fulfill({ status: 204 });
  });
  await page.goto(`/groups/${groupId}/members`);
  await expect(page).toHaveTitle('Coprogo');
  await page.getByRole('textbox', { name: 'Adresse email Google' }).fill('frank@example.com');
  await page.getByRole('button', { name: 'Ajouter au groupe', exact: true }).click();
  await expect(page.locator('.members-page__list')).toContainText('frank@example.com');
  await page.reload();
  await expect(page.locator('.members-page__list')).toContainText('frank@example.com');
});
