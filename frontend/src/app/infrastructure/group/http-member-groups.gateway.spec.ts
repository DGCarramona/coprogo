import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApiClient } from '../api/provide-api-client';
import { ApiClientError } from '../api/api-client.error';
import { HttpMemberGroupsGateway } from './http-member-groups.gateway';

describe('HttpMemberGroupsGateway', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  describe('list', () => {
    it('loads all memberships of the authenticated member and maps dates', async () => {
      const result = TestBed.inject(HttpMemberGroupsGateway).list();
      const request = TestBed.inject(HttpTestingController).expectOne(
        'http://localhost:8080/api/groups',
      );
      expect(request.request.method).toBe('GET');
      expect(request.request.params.keys()).toEqual([]);
      request.flush([
        { group: 'group-1', createdBy: 'alice@example.com', createdAt: '2026-01-01T00:00:00Z' },
      ]);

      await expect(result).resolves.toEqual([
        {
          groupId: 'group-1',
          createdBy: 'alice@example.com',
          createdAt: new Date('2026-01-01T00:00:00Z'),
        },
      ]);
    });

    it('maps API errors', async () => {
      const result = TestBed.inject(HttpMemberGroupsGateway).list();
      TestBed.inject(HttpTestingController)
        .expectOne('http://localhost:8080/api/groups')
        .flush(
          { message: 'Groupes indisponibles' },
          { status: 503, statusText: 'Service Unavailable' },
        );

      await expect(result).rejects.toBeInstanceOf(ApiClientError);
      await expect(result).rejects.toMatchObject({ status: 503, message: 'Groupes indisponibles' });
    });
  });
});
