import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import { ApiClientError } from '../api/api-client.error';
import { provideApiClient } from '../api/provide-api-client';
import { HttpGroupCreationGateway } from './http-group-creation.gateway';

describe('HttpGroupCreationGateway', () => {
  let gateway: HttpGroupCreationGateway;
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

    gateway = TestBed.inject(HttpGroupCreationGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('posts a group creation and maps its id', async () => {
    const creation = gateway.create();

    const request = httpTestingController.expectOne('http://localhost:8080/api/groups');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    request.flush({ group: '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93' });

    await expect(creation).resolves.toBe('9d88fb48-4e2c-4a89-8b6b-509ed8f00b93');
  });

  it('maps an API failure to an ApiClientError', async () => {
    const creation = gateway.create();

    const request = httpTestingController.expectOne('http://localhost:8080/api/groups');

    request.flush(
      { message: 'Le groupe ne peut pas etre cree.' },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(creation).rejects.toBeInstanceOf(ApiClientError);
    await expect(creation).rejects.toMatchObject({
      message: 'Le groupe ne peut pas etre cree.',
      status: 409,
    });
  });
});
