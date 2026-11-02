import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  GroupInvitationsPort,
  SentGroupInvitation,
} from '../../application/group/group-invitations.port';
import { GroupInvitationsViewModel } from './group-invitations.view-model';

class Invitations extends GroupInvitationsPort {
  rows: readonly SentGroupInvitation[] = [
    {
      invitation: 'inv-1',
      invitedMember: 'dana@example.com',
      invitedBy: 'bob@example.com',
      invitedAt: new Date('2026-01-01'),
      canCancel: true,
    },
  ];
  cancelled: string[] = [];
  failure = false;
  pending: Promise<void> = Promise.resolve();
  override async list() {
    if (this.failure) throw new Error('unavailable');
    return this.rows;
  }
  override async cancel(group: string, invitation: string) {
    this.cancelled.push(`${group}:${invitation}`);
    await this.pending;
    this.rows = [];
  }
}
describe('GroupInvitationsViewModel', () => {
  let port: Invitations;
  let client: QueryClient;
  function create() {
    return new GroupInvitationsViewModel(port, client, TestBed.inject(Injector));
  }
  async function ready(vm: GroupInvitationsViewModel) {
    await vi.waitFor(() => {
      TestBed.tick();
      expect(vm.isLoading()).toBe(false);
    });
  }
  beforeEach(() => {
    port = new Invitations();
    client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(client)] });
  });
  afterEach(() => client.clear());
  describe('setGroup', () => {
    it('loads invitations including cancellation permissions', async () => {
      const vm = create();
      vm.setGroup('group-1');
      await ready(vm);
      expect(vm.invitations()).toEqual(port.rows);
    });
  });
  describe('retry', () => {
    it('recovers from a loading error', async () => {
      port.failure = true;
      const vm = create();
      vm.setGroup('group-1');
      await ready(vm);
      expect(vm.hasLoadError()).toBe(true);
      port.failure = false;
      vm.retry();
      await vi.waitFor(() => {
        TestBed.tick();
        expect(vm.invitations()).toEqual(port.rows);
      });
    });
  });
  describe('cancel', () => {
    it('cancels and refreshes the list', async () => {
      const vm = create();
      vm.setGroup('group-1');
      await ready(vm);
      await vm.cancel('inv-1');
      await vi.waitFor(() => {
        TestBed.tick();
        expect(vm.invitations()).toEqual([]);
      });
      expect(port.cancelled).toEqual(['group-1:inv-1']);
    });
    it('does not cancel without permission', async () => {
      port.rows = port.rows.map((row) => ({ ...row, canCancel: false }));
      const vm = create();
      vm.setGroup('group-1');
      await ready(vm);
      await vm.cancel('inv-1');
      expect(port.cancelled).toEqual([]);
    });
    it('prevents duplicate cancellation and reports a failure without losing the list', async () => {
      let reject!: (reason: Error) => void;
      port.pending = new Promise((_, fail) => {
        reject = fail;
      });
      const vm = create();
      vm.setGroup('group-1');
      await ready(vm);
      const result = vm.cancel('inv-1');
      await vm.cancel('inv-1');
      reject(new Error('failed'));
      await result;
      expect(port.cancelled).toEqual(['group-1:inv-1']);
      expect(vm.cancelError()).toBe('failed');
      expect(vm.invitations()).toEqual(port.rows);
    });
  });
});
