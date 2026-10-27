import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApiClient } from '../api/provide-api-client';
import { ApiClientError } from '../api/api-client.error';
import { HttpGroupRenamingGateway } from './http-group-renaming.gateway';

describe('HttpGroupRenamingGateway', () => {
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
  describe('rename', () => {
    it('sends the new name to the group', async () => {
      const result = TestBed.inject(HttpGroupRenamingGateway).rename(
        'group-1',
        'Appartement familial',
      );
      const request = TestBed.inject(HttpTestingController).expectOne(
        'http://localhost:8080/api/groups/group-1/name',
      );
      expect(request.request.method).toBe('PATCH');
      expect(request.request.body).toEqual({ name: 'Appartement familial' });
      request.flush(null, { status: 204, statusText: 'No Content' });
      await expect(result).resolves.toBeUndefined();
    });
    it('exposes a forbidden rename as an API error', async () => {
      const result = TestBed.inject(HttpGroupRenamingGateway).rename('group-1', 'Maison');
      TestBed.inject(HttpTestingController)
        .expectOne('http://localhost:8080/api/groups/group-1/name')
        .flush({ message: 'Réservé au créateur' }, { status: 403, statusText: 'Forbidden' });
      await expect(result).rejects.toBeInstanceOf(ApiClientError);
      await expect(result).rejects.toMatchObject({ status: 403, message: 'Réservé au créateur' });
    });
  });
});
