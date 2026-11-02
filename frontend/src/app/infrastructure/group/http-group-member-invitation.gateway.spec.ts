import { MemberEmail } from '../../domain/group/member-email';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApiClient } from '../api/provide-api-client';
import { ApiClientError } from '../api/api-client.error';
import { HttpGroupMemberInvitationGateway } from './http-group-member-invitation.gateway';

describe('HttpGroupMemberInvitationGateway', () => {
  let gateway: HttpGroupMemberInvitationGateway;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });
    gateway = TestBed.inject(HttpGroupMemberInvitationGateway);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  describe('invite', () => {
    it('posts an invitation scoped to the group', async () => {
      const result = gateway.invite('group-1', MemberEmail('bob@example.com'));
      const request = http.expectOne('http://localhost:8080/api/groups/group-1/invitations');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ invitedMember: 'bob@example.com' });
      request.flush(null, { status: 204, statusText: 'No Content' });
      await expect(result).resolves.toBeUndefined();
    });
    it('translates a forbidden request into an application error', async () => {
      const result = gateway.invite('group-1', MemberEmail('bob@example.com'));
      http
        .expectOne('http://localhost:8080/api/groups/group-1/invitations')
        .flush({}, { status: 403, statusText: 'Forbidden' });
      await expect(result).rejects.toBeInstanceOf(ApiClientError);
      await expect(result).rejects.toMatchObject({ status: 403 });
    });
  });
});
