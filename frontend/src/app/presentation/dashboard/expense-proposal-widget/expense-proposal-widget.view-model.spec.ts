import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of, throwError } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import { GroupMember } from '../../../domain/group/group-member';
import { ExpenseProposalWidgetViewModel } from './expense-proposal-widget.view-model';

describe('ExpenseProposalWidgetViewModel', () => {
  let queryClient: QueryClient;
  let port: StubGroupMembersPort;
  let injector: Injector;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    TestBed.configureTestingModule({
      providers: [provideTanStackQuery(queryClient)],
    });
    port = new StubGroupMembersPort();
    injector = TestBed.inject(Injector);
  });

  it('does not request members before initialization', () => {
    createViewModel();

    expect(port.requestedGroupIds).toEqual([]);
  });

  it('loads members and stores them under the scoped query key', async () => {
    const viewModel = createViewModel();

    viewModel.initialize('group-1');

    await waitFor(() => viewModel.members().length === 2);

    expect(viewModel.members()).toEqual(port.members);
    expect(queryClient.getQueryData(['groups', 'group-1', 'members'])).toEqual(port.members);
  });

  it('reuses fresh members for the same group across view model instances', async () => {
    const firstViewModel = createViewModel();
    firstViewModel.initialize('group-1');
    await waitFor(() => firstViewModel.members().length === 2);

    const secondViewModel = createViewModel();
    secondViewModel.initialize('group-1');
    await waitFor(() => secondViewModel.members().length === 2);

    expect(port.requestedGroupIds).toEqual(['group-1']);
  });

  it('exposes a member loading error', async () => {
    port.failure = new Error('Membres indisponibles');
    const viewModel = createViewModel();

    viewModel.initialize('group-1');

    await waitFor(() => viewModel.hasLoadError());

    expect(viewModel.errorMessage()).toBe('Membres indisponibles');
  });

  it('retries after a member loading error', async () => {
    port.failure = new Error('Membres indisponibles');
    const viewModel = createViewModel();
    viewModel.initialize('group-1');
    await waitFor(() => viewModel.hasLoadError());

    port.failure = null;
    viewModel.retry();

    await waitFor(() => viewModel.members().length === 2);

    expect(port.requestedGroupIds).toEqual(['group-1', 'group-1']);
  });

  const createViewModel = () => new ExpenseProposalWidgetViewModel(port, injector);
});

class StubGroupMembersPort extends GroupMembersPort {
  members: readonly GroupMember[] = [
    { member: 'alice@example.com', joinedAt: new Date('2026-08-01T10:00:00.000Z') },
    { member: 'bob@example.com', joinedAt: new Date('2026-08-02T10:00:00.000Z') },
  ];
  requestedGroupIds: string[] = [];
  failure: Error | null = null;

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    this.requestedGroupIds.push(groupId);
    const failure = this.failure;

    return failure === null ? of(this.members) : throwError(() => failure);
  }
}

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
