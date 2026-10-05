import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import {
  AUTHENTICATED_API_HTTP_CLIENT,
  DIRECT_HTTP_CLIENT,
  provideCoprogoHttpClients,
} from './named-http-clients';

describe('provideCoprogoHttpClients', () => {
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideCoprogoHttpClients(),
        provideHttpClientTesting(),
        { provide: GoogleIdTokenPort, useClass: StubGoogleIdTokenPort },
      ],
    });
    TestBed.inject(GoogleIdTokenPort).store('google-id-token');
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('provides the standard authenticated API client and a distinct direct client', async () => {
    const authenticatedClient = TestBed.inject(AUTHENTICATED_API_HTTP_CLIENT);
    const directClient = TestBed.inject(DIRECT_HTTP_CLIENT);

    expect(authenticatedClient).toBe(TestBed.inject(HttpClient));
    expect(directClient).not.toBe(authenticatedClient);

    const authenticatedResponse = firstValueFrom(authenticatedClient.get('/api/groups'));
    const directResponse = firstValueFrom(directClient.get('https://documents.example/upload'));

    const authenticatedRequest = httpTestingController.expectOne('/api/groups');
    expect(authenticatedRequest.request.headers.get('Authorization')).toBe(
      'Bearer google-id-token',
    );
    authenticatedRequest.flush({});

    const directRequest = httpTestingController.expectOne('https://documents.example/upload');
    expect(directRequest.request.headers.has('Authorization')).toBe(false);
    directRequest.flush({});

    await expect(authenticatedResponse).resolves.toEqual({});
    await expect(directResponse).resolves.toEqual({});
  });
});
