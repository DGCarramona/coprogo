import { expect, groupId, test } from './fixtures/group-page.js';

test('lists pending invitations, cancels an authorized invitation and refreshes after inviting', async ({
  page,
}) => {
  let invitations = [
    {
      invitation: 'inv-1',
      invitedMember: 'dana@example.com',
      invitedBy: 'bob@example.com',
      invitedAt: '2026-01-01T00:00:00Z',
      canCancel: true,
    },
    {
      invitation: 'inv-2',
      invitedMember: 'eve@example.com',
      invitedBy: 'alice@example.com',
      invitedAt: '2026-01-02T00:00:00Z',
      canCancel: false,
    },
  ];
  await page.route(`http://localhost:8080/api/groups/${groupId}`, (route) =>
    route.fulfill({
      json: {
        group: groupId,
        name: 'Appartement',
        members: [{ member: 'alice@example.com', joinedAt: '2026-01-01T00:00:00Z' }],
        pendingInvitations: invitations,
      },
    }),
  );
  await page.route(`http://localhost:8080/api/groups/${groupId}/invitations/*`, (route) => {
    expect(route.request().method()).toBe('DELETE');
    invitations = invitations.filter((invitation) => invitation.invitation !== 'inv-1');
    return route.fulfill({ status: 204 });
  });
  await page.route(`http://localhost:8080/api/groups/${groupId}/invitations`, (route) => {
    invitations.push({
      invitation: 'inv-3',
      invitedMember: 'frank@example.com',
      invitedBy: 'alice@example.com',
      invitedAt: '2026-01-03T00:00:00Z',
      canCancel: true,
    });
    return route.fulfill({ status: 204 });
  });
  await page.goto(`/groups/${groupId}/members`);
  await expect(page).toHaveTitle('Coprogo');
  const widget = page.locator('app-group-invitations-widget');
  await expect(widget.getByText('dana@example.com', { exact: true })).toBeVisible();
  await expect(widget.getByText('eve@example.com', { exact: true })).toBeVisible();
  await expect(widget.getByRole('button')).toHaveCount(1);
  await widget.getByRole('button', { name: 'Annuler l’invitation de dana@example.com' }).click();
  await expect(widget.getByText('dana@example.com', { exact: true })).toHaveCount(0);
  await expect(widget.getByText('eve@example.com', { exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: 'Adresse email Google' }).fill('frank@example.com');
  await page.getByRole('button', { name: 'Créer l’invitation', exact: true }).click();
  await expect(widget.getByText('frank@example.com', { exact: true })).toBeVisible();
  await page.reload();
  await expect(widget.getByText('frank@example.com', { exact: true })).toBeVisible();
});
