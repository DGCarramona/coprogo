import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StubGoogleIdTokenPort } from '../../../../__test__/app/application/auth/stub-google-id-token.port';
import { GoogleIdTokenPort } from '../../application/auth/google-id-token.port';
import type { OwnershipShareTimeline } from '../../domain/revenue/ownership-share-timeline';
import { ApiClientError } from '../api/api-client.error';
import { apiAuthInterceptor } from '../api/api-auth.interceptor';
import { provideApiClient } from '../api/provide-api-client';
import { HttpOwnershipShareTimelineGateway } from './http-ownership-share-timeline.gateway';

const GROUP_ID = '9d88fb48-4e2c-4a89-8b6b-509ed8f00b93';

describe('HttpOwnershipShareTimelineGateway', () => {
  let gateway: HttpOwnershipShareTimelineGateway;
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
    gateway = TestBed.inject(HttpOwnershipShareTimelineGateway);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  describe('getByGroup', () => {
    it('maps the group ownership-share timeline', async () => {
      const timeline = gateway.getByGroup(GROUP_ID);

      expectTimelineRequest().flush(timelineResponse());

      const expected: OwnershipShareTimeline = {
        groupId: GROUP_ID,
        changes: [
          {
            id: 'change-1',
            effectiveDate: new Date(2026, 0, 1),
            recordedBy: 'alice@example.com',
            recordedAt: new Date('2025-12-15T10:00:00Z'),
            shares: [
              { member: 'alice@example.com', percentage: 60 },
              { member: 'bob@example.com', percentage: 40 },
            ],
          },
        ],
      };
      await expect(timeline).resolves.toEqual(expected);
    });

    it.each([
      {
        name: 'effective date',
        response: timelineResponse({ effectiveDate: '2026-02-30' }),
        message: 'Date d effet des quotes-parts invalide: 2026-02-30.',
      },
      {
        name: 'recording date',
        response: timelineResponse({ recordedAt: 'date-invalide' }),
        message: 'Date d enregistrement des quotes-parts invalide: date-invalide.',
      },
    ])('rejects an invalid $name', async ({ response, message }) => {
      const timeline = gateway.getByGroup(GROUP_ID);

      expectTimelineRequest().flush(response);

      await expect(timeline).rejects.toThrow(message);
    });

    it('rejects a response for another group', async () => {
      const timeline = gateway.getByGroup(GROUP_ID);

      expectTimelineRequest().flush(timelineResponse({}, 'another-group'));

      await expect(timeline).rejects.toThrow('Historique des quotes-parts incoherent.');
    });

    it('maps an API failure to an ApiClientError', async () => {
      const timeline = gateway.getByGroup(GROUP_ID);

      expectTimelineRequest().flush(
        { message: 'Historique indisponible.' },
        { status: 503, statusText: 'Service Unavailable' },
      );

      await expect(timeline).rejects.toBeInstanceOf(ApiClientError);
      await expect(timeline).rejects.toMatchObject({
        message: 'Historique indisponible.',
        status: 503,
      });
    });
  });

  const expectTimelineRequest = () =>
    httpTestingController.expectOne(
      `http://localhost:8080/api/groups/${GROUP_ID}/ownership-shares`,
    );
});

const timelineResponse = (changeOverrides: Record<string, unknown> = {}, group = GROUP_ID) => ({
  group,
  changes: [
    {
      change: 'change-1',
      effectiveDate: '2026-01-01',
      recordedBy: 'alice@example.com',
      recordedAt: '2025-12-15T10:00:00Z',
      shares: [
        { member: 'alice@example.com', percentage: 60 },
        { member: 'bob@example.com', percentage: 40 },
      ],
      ...changeOverrides,
    },
  ],
});
