import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { MemberGroupsViewModel } from './member-groups.view-model';
import { memberGroupsQueryKey } from './member-groups-query';

describe('MemberGroupsViewModel', () => {
  describe('retry', () => {
    it('refreshes the displayed groups when memberships change', async () => {
      const queryClient = new QueryClient();
      let groups = [
        { groupId: 'group-1', createdBy: 'alice@example.com', createdAt: new Date('2026-01-01') },
      ];
      TestBed.configureTestingModule({
        providers: [
          provideTanStackQuery(queryClient),
          { provide: MemberGroupsPort, useValue: { list: async () => groups } },
          MemberGroupsViewModel,
        ],
      });
      const viewModel = TestBed.inject(MemberGroupsViewModel);
      TestBed.tick();
      await vi.waitFor(() => expect(viewModel.groups()).toEqual(groups));
      const joined = {
        groupId: 'group-2',
        createdBy: 'bob@example.com',
        createdAt: new Date('2026-02-01'),
      };
      groups = [...groups, joined];

      await queryClient.invalidateQueries({ queryKey: memberGroupsQueryKey });

      await vi.waitFor(() => expect(viewModel.groups()).toEqual(groups));
      groups = [];
      viewModel.retry();
      await vi.waitFor(() => expect(viewModel.groups()).toEqual([]));
    });
  });
});
