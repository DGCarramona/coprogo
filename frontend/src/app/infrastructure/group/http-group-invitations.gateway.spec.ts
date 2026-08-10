import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { provideApiClient } from '../api/provide-api-client';
import { HttpGroupInvitationsGateway } from './http-group-invitations.gateway';

describe('HttpGroupInvitationsGateway', () => {
  let gateway: HttpGroupInvitationsGateway;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        {
          provide: GoogleIdTokenPort,
          useClass: StubGoogleIdTokenPort,
        },
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });

    gateway = TestBed.inject(HttpGroupInvitationsGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('listPending', () => {
    it('gets and maps pending invitations', async () => {
      const invitations = gateway.listPending();

      const request = httpTestingController.expectOne(
        'http://localhost:8080/api/group-invitations/pending',
      );

      expect(request.request.method).toBe('GET');

      request.flush([
        {
          invitation: '3e08d3bb-9a94-4f05-af6a-e3afc408835a',
          group: '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93',
          invitedMember: 'bob@example.com',
          invitedBy: 'alice@example.com',
          invitedAt: '2026-08-10T09:30:00.000Z',
        },
      ]);

      await expect(invitations).resolves.toEqual([
        {
          invitationId: '3e08d3bb-9a94-4f05-af6a-e3afc408835a',
          groupId: '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93',
          invitedMember: 'bob@example.com',
          invitedBy: 'alice@example.com',
          invitedAt: new Date('2026-08-10T09:30:00.000Z'),
        },
      ]);
    });

    it('maps an API failure to an ApiClientError', async () => {
      const invitations = gateway.listPending();

      const request = httpTestingController.expectOne(
        'http://localhost:8080/api/group-invitations/pending',
      );

      request.flush(
        { message: 'Les invitations sont indisponibles.' },
        { status: 503, statusText: 'Service Unavailable' },
      );

      await expect(invitations).rejects.toBeInstanceOf(ApiClientError);
      await expect(invitations).rejects.toMatchObject({
        message: 'Les invitations sont indisponibles.',
        status: 503,
      });
    });
  });

  describe('accept', () => {
    it('posts an invitation acceptance and resolves to undefined', async () => {
      const acceptance = gateway.accept('3e08d3bb-9a94-4f05-af6a-e3afc408835a');

      const request = httpTestingController.expectOne(
        'http://localhost:8080/api/group-invitations/3e08d3bb-9a94-4f05-af6a-e3afc408835a/accept',
      );

      expect(request.request.method).toBe('POST');
      expect(request.request.body).toBeNull();

      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(acceptance).resolves.toBeUndefined();
    });

    it('maps an API failure to an ApiClientError', async () => {
      const acceptance = gateway.accept('3e08d3bb-9a94-4f05-af6a-e3afc408835a');

      const request = httpTestingController.expectOne(
        'http://localhost:8080/api/group-invitations/3e08d3bb-9a94-4f05-af6a-e3afc408835a/accept',
      );

      request.flush(
        { message: "L'invitation est deja acceptee." },
        { status: 409, statusText: 'Conflict' },
      );

      await expect(acceptance).rejects.toBeInstanceOf(ApiClientError);
      await expect(acceptance).rejects.toMatchObject({
        message: "L'invitation est deja acceptee.",
        status: 409,
      });
    });
  });
});
