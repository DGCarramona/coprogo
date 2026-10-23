import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { DirectReimbursementRecordingCommand } from '../../application/reimbursement/direct-reimbursement-recording.port';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpDirectReimbursementRecordingGateway } from './http-direct-reimbursement-recording.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpDirectReimbursementRecordingGateway', () => {
  let gateway: HttpDirectReimbursementRecordingGateway;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiAuthInterceptor])),
        provideHttpClientTesting(),
        { provide: GoogleIdTokenPort, useClass: StubGoogleIdTokenPort },
        provideApiClient({ basePath: 'http://localhost:8080' }),
      ],
    });
    gateway = TestBed.inject(HttpDirectReimbursementRecordingGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('record', () => {
    it('records the reimbursement through the generated API client', async () => {
      const command = directReimbursementCommand();

      const recording = gateway.record(command);

      const request = httpTestingController.expectOne(
        `http://localhost:8080/api/groups/${GROUP_ID}/reimbursements/direct`,
      );
      expect({ method: request.request.method, body: request.request.body }).toEqual({
        method: 'POST',
        body: {
          paidBy: 'bob@example.com',
          amountInCents: 2500,
          reimbursedAt: '2026-10-01T09:30:00.000Z',
        },
      });
      request.flush(null, { status: 204, statusText: 'No Content' });

      await expect(recording).resolves.toBeUndefined();
    });

    it('maps an API failure to an ApiClientError', async () => {
      const recording = gateway.record(directReimbursementCommand());

      httpTestingController
        .expectOne(`http://localhost:8080/api/groups/${GROUP_ID}/reimbursements/direct`)
        .flush(
          { message: 'Le remboursement ne peut pas etre enregistre.' },
          { status: 422, statusText: 'Unprocessable Entity' },
        );

      await expect(recording).rejects.toBeInstanceOf(ApiClientError);
      await expect(recording).rejects.toMatchObject({
        message: 'Le remboursement ne peut pas etre enregistre.',
        status: 422,
      });
    });
  });
});

const directReimbursementCommand = (): DirectReimbursementRecordingCommand => ({
  groupId: GROUP_ID,
  paidBy: 'bob@example.com',
  amountInCents: 2500,
  reimbursedAt: new Date('2026-10-01T09:30:00.000Z'),
});
