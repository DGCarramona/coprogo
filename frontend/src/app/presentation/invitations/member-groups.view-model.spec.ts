import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { MemberGroupsViewModel } from './member-groups.view-model';
import { memberGroupsQueryKey } from './member-groups-query';
import { GroupRenamingPort } from '../../application/group/group-renaming.port';
import { StubGroupRenamingPort } from '../../../../__test__/app/application/group/stub-group-renaming.port';

describe('MemberGroupsViewModel', () => {
  describe('renameGroup', () => {
    const own = {
      groupId: 'group-1',
      createdBy: 'alice@example.com',
      createdAt: new Date('2026-01-01'),
      name: 'Appartement',
      canRename: true,
    };

    it('renames and refreshes the list', async () => {
      const { viewModel, port, queryClient } = createRenameFixture();
      viewModel.beginRename(own);
      viewModel.setName('  Maison  ');
      queryClient.setQueryData(memberGroupsQueryKey, [own]);
      await viewModel.renameGroup();
      expect(port.commands).toEqual([{ groupId: 'group-1', name: 'Maison' }]);
      expect(viewModel.editingGroupId()).toBeNull();
    });

    it.each(['', '   ', 'a'.repeat(101)])('does not send an invalid name %j', async (name) => {
      const { viewModel, port } = createRenameFixture();
      viewModel.beginRename(own);
      viewModel.setName(name);
      await viewModel.renameGroup();
      expect(port.commands).toEqual([]);
      expect(viewModel.renameError()).not.toBeNull();
    });

    it('keeps the draft and explains a refused rename', async () => {
      const { viewModel, port } = createRenameFixture();
      port.failure = new Error('Réservé au créateur');
      viewModel.beginRename(own);
      viewModel.setName('Maison');
      await viewModel.renameGroup();
      expect(viewModel.editingGroupId()).toBe('group-1');
      expect(viewModel.draftName()).toBe('Maison');
      expect(viewModel.renameError()).toBe('Réservé au créateur');
    });

    it('does not open editing for another member and can cancel a draft', () => {
      const { viewModel } = createRenameFixture();
      viewModel.beginRename({ ...own, canRename: false });
      expect(viewModel.editingGroupId()).toBeNull();
      viewModel.beginRename(own);
      viewModel.cancelRename();
      expect(viewModel.editingGroupId()).toBeNull();
    });
  });
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
          { provide: GroupRenamingPort, useClass: StubGroupRenamingPort },
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

function createRenameFixture() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const port = new StubGroupRenamingPort();
  TestBed.configureTestingModule({
    providers: [
      provideTanStackQuery(queryClient),
      { provide: GroupRenamingPort, useValue: port },
      { provide: MemberGroupsPort, useValue: { list: async () => [] } },
      MemberGroupsViewModel,
    ],
  });
  const viewModel = TestBed.inject(MemberGroupsViewModel);
  TestBed.tick();
  return { viewModel, port, queryClient };
}
