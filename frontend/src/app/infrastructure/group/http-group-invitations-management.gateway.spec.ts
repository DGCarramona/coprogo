import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApiClient } from '../api/provide-api-client';
import { HttpGroupInvitationsManagementGateway } from './http-group-invitations-management.gateway';

describe('HttpGroupInvitationsManagementGateway', () => {
  let gateway: HttpGroupInvitationsManagementGateway;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });
    gateway = TestBed.inject(HttpGroupInvitationsManagementGateway);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  describe('list', () => {
    it('maps pending invitations and permissions', async () => {
      const result = gateway.list('group-1');
      http.expectOne('http://localhost:8080/api/groups/group-1').flush({
        pendingInvitations: [
          {
            invitation: 'inv-1',
            invitedMember: 'dana@example.com',
            invitedBy: 'bob@example.com',
            invitedAt: '2026-01-01T00:00:00Z',
            canCancel: true,
          },
        ],
      });
      await expect(result).resolves.toEqual([
        {
          invitation: 'inv-1',
          invitedMember: 'dana@example.com',
          invitedBy: 'bob@example.com',
          invitedAt: new Date('2026-01-01'),
          canCancel: true,
        },
      ]);
    });
  });
  describe('cancel', () => {
    it('deletes the invitation in its group', async () => {
      const result = gateway.cancel('group-1', 'inv-1');
      const request = http.expectOne('http://localhost:8080/api/groups/group-1/invitations/inv-1');
      expect(request.request.method).toBe('DELETE');
      request.flush(null, { status: 204, statusText: 'No Content' });
      await expect(result).resolves.toBeUndefined();
    });
    it('translates forbidden cancellation errors', async () => {
      const result = gateway.cancel('group-1', 'inv-1');
      http
        .expectOne('http://localhost:8080/api/groups/group-1/invitations/inv-1')
        .flush({}, { status: 403, statusText: 'Forbidden' });
      await expect(result).rejects.toMatchObject({ status: 403 });
    });
  });
});
