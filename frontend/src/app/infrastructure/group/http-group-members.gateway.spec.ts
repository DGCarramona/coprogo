import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, Observable } from 'rxjs';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { provideApiClient } from '../api/provide-api-client';
import { HttpGroupMembersGateway } from './http-group-members.gateway';

describe('HttpGroupMembersGateway', () => {
  let gateway: HttpGroupMembersGateway;
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

    gateway = TestBed.inject(HttpGroupMembersGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('returns an observable of every group member with mapped join dates', async () => {
    const members$ = gateway.listByGroup('9d88fb48-4e2c-4a89-8b6b-509ed8f00b93');

    expect(members$).toBeInstanceOf(Observable);

    const members = firstValueFrom(members$);

    const request = httpTestingController.expectOne(
      'http://localhost:8080/api/groups/9d88fb48-4e2c-4a89-8b6b-509ed8f00b93',
    );

    expect(request.request.method).toBe('GET');

    request.flush({
      group: '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93',
      createdBy: 'alice@example.com',
      createdAt: '2026-08-02T10:00:00.000Z',
      members: [
        { member: 'alice@example.com', joinedAt: '2026-08-01T10:00:00.000Z' },
        { member: 'bob@example.com', joinedAt: '2026-08-02T10:00:00.000Z' },
      ],
      pendingInvitations: [],
    });

    await expect(members).resolves.toEqual([
      { member: 'alice@example.com', joinedAt: new Date('2026-08-01T10:00:00.000Z') },
      { member: 'bob@example.com', joinedAt: new Date('2026-08-02T10:00:00.000Z') },
    ]);
  });

  it('maps an API failure to an ApiClientError', async () => {
    const members = firstValueFrom(gateway.listByGroup('9d88fb48-4e2c-4a89-8b6b-509ed8f00b93'));

    const request = httpTestingController.expectOne(
      'http://localhost:8080/api/groups/9d88fb48-4e2c-4a89-8b6b-509ed8f00b93',
    );

    request.flush(
      { message: 'Les membres du groupe sont indisponibles.' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    await expect(members).rejects.toBeInstanceOf(ApiClientError);
    await expect(members).rejects.toMatchObject({
      message: 'Les membres du groupe sont indisponibles.',
      status: 503,
    });
  });
});
