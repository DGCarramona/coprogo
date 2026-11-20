import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { of, throwError } from 'rxjs';
import { GroupMembersPort } from '../../application/group/group-members.port';
import { GroupMemberAdditionPort } from '../../application/group/group-member-addition.port';
import { GroupMembersPageViewModel } from './group-members-page.view-model';
import { groupMembersQueryKey } from '../shared/group-members/group-members-query';
import { GroupMember } from '../../domain/group/group-member';

class StubMembers extends GroupMembersPort {
  requests: string[] = [];
  failure = false;
  rows: GroupMember[] = [
    { member: 'alice@example.com', joinedAt: new Date('2026-01-01'), addedBy: null },
    { member: 'bob@example.com', joinedAt: new Date('2026-02-01'), addedBy: 'alice@example.com' },
  ];
  override listByGroup(groupId: string) {
    this.requests.push(groupId);
    return this.failure ? throwError(() => new Error('unavailable')) : of(this.rows);
  }
}
class StubMemberAdditions extends GroupMemberAdditionPort {
  commands: { groupId: string; email: string }[] = [];
  pending: Promise<void> = Promise.resolve();
  constructor(private readonly onAdd: (email: string) => void = () => undefined) {
    super();
  }
  override addMember(groupId: string, email: string): Promise<void> {
    this.commands.push({ groupId, email });
    this.onAdd(email);
    return this.pending;
  }
}

describe('GroupMembersPageViewModel', () => {
  let members: StubMembers;
  let additions: StubMemberAdditions;
  let client: QueryClient;
  beforeEach(() => {
    members = new StubMembers();
    additions = new StubMemberAdditions((email) => {
      members.rows = [
        ...members.rows,
        { member: email, joinedAt: new Date('2026-03-01'), addedBy: 'alice@example.com' },
      ];
    });
    client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(client)] });
  });
  function create(groupId: string | null = 'group-1') {
    const route = {
      snapshot: { paramMap: convertToParamMap(groupId === null ? {} : { groupId }) },
    } as ActivatedRoute;
    return new GroupMembersPageViewModel(
      route,
      members,
      additions,
      client,
      TestBed.inject(Injector),
    );
  }
  async function ready(vm: GroupMembersPageViewModel) {
    await vi.waitFor(() => {
      TestBed.tick();
      expect(vm.isReady()).toBe(true);
    });
  }
  describe('members', () => {
    it('loads the route group and preserves joining dates in the shared query cache', async () => {
      const vm = create();
      await ready(vm);
      expect(members.requests).toEqual(['group-1']);
      expect(vm.members()).toEqual([
        { member: 'alice@example.com', joinedAt: new Date('2026-01-01'), addedBy: null },
        {
          member: 'bob@example.com',
          joinedAt: new Date('2026-02-01'),
          addedBy: 'alice@example.com',
        },
      ]);
      expect(client.getQueryData(groupMembersQueryKey('group-1'))).toEqual(vm.members());
    });
    it('rejects a missing group without requesting members or adding a member', async () => {
      const vm = create(null);
      await vm.addMember('alice@example.com');
      expect(vm.hasLoadError()).toBe(true);
      expect(vm.errorMessage()).toBe('errors.groups.noneSelected');
      expect({ reads: members.requests, writes: additions.commands }).toEqual({
        reads: [],
        writes: [],
      });
    });
  });
  describe('retry', () => {
    it('recovers a failed members request', async () => {
      members.failure = true;
      const vm = create();
      await vi.waitFor(() => {
        TestBed.tick();
        expect(vm.hasLoadError()).toBe(true);
      });
      members.failure = false;
      vm.retry();
      await ready(vm);
      expect(members.requests).toEqual(['group-1', 'group-1']);
    });
  });
  describe('addMember', () => {
    it.each(['', 'bad', 'a@@example.com', 'a b@example.com'])(
      'rejects invalid email %s',
      async (email) => {
        const vm = create();
        await ready(vm);
        await vm.addMember(email);
        expect(vm.addMemberError()).toBe('validation.members.email');
        expect(additions.commands).toEqual([]);
      },
    );
    it('normalizes the email and refreshes the group members after adding a member', async () => {
      const vm = create();
      await ready(vm);
      await vm.addMember('  CHARLIE@Example.com  ');
      expect(additions.commands).toEqual([{ groupId: 'group-1', email: 'charlie@example.com' }]);
      expect(vm.addedMember()).toBe('charlie@example.com');
      expect(members.rows.map((member) => member.member)).toEqual([
        'alice@example.com',
        'bob@example.com',
        'charlie@example.com',
      ]);
      expect(members.requests).toEqual(['group-1', 'group-1']);
      expect(client.getQueryData(groupMembersQueryKey('group-1'))).toEqual(members.rows);
      await vi.waitFor(() => {
        TestBed.tick();
        expect(vm.members().map((member) => member.member)).toEqual([
          'alice@example.com',
          'bob@example.com',
          'charlie@example.com',
        ]);
      });
    });
    it('prevents duplicate submissions while pending', async () => {
      let resolve!: () => void;
      additions.pending = new Promise((done) => {
        resolve = done;
      });
      const vm = create();
      await ready(vm);
      const request = vm.addMember('charlie@example.com');
      const duplicate = vm.addMember('dana@example.com');
      await Promise.resolve();
      await Promise.resolve();
      expect(additions.commands).toEqual([{ groupId: 'group-1', email: 'charlie@example.com' }]);
      resolve();
      await Promise.all([request, duplicate]);
    });
    it('clears a previous success on failure and allows retry', async () => {
      const vm = create();
      await ready(vm);
      await vm.addMember('charlie@example.com');
      additions.pending = Promise.reject(new Error('failed'));
      await vm.addMember('dana@example.com');
      expect(vm.addedMember()).toBeNull();
      expect(vm.addMemberError()).toBe('failed');
      expect(vm.isAddingMember()).toBe(false);
      additions.pending = Promise.resolve();
      await vm.addMember('dana@example.com');
      expect(vm.addedMember()).toBe('dana@example.com');
    });
  });
});
