import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { of, throwError } from 'rxjs';
import { GroupMembersPort } from '../../application/group/group-members.port';
import { GroupMemberInvitationPort } from '../../application/group/group-member-invitation.port';
import { GroupMembersPageViewModel } from './group-members-page.view-model';
import { groupMembersQueryKey } from '../shared/group-members/group-members-query';

class StubMembers extends GroupMembersPort {
  requests: string[] = [];
  failure = false;
  override listByGroup(groupId: string) {
    this.requests.push(groupId);
    return this.failure
      ? throwError(() => new Error('unavailable'))
      : of([
          { member: 'alice@example.com', joinedAt: new Date('2026-01-01') },
          { member: 'bob@example.com', joinedAt: new Date('2026-02-01') },
        ]);
  }
}
class StubInvitations extends GroupMemberInvitationPort {
  commands: { groupId: string; email: string }[] = [];
  pending: Promise<void> = Promise.resolve();
  override invite(groupId: string, email: string): Promise<void> {
    this.commands.push({ groupId, email });
    return this.pending;
  }
}

describe('GroupMembersPageViewModel', () => {
  let members: StubMembers;
  let invitations: StubInvitations;
  let client: QueryClient;
  beforeEach(() => {
    members = new StubMembers();
    invitations = new StubInvitations();
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
      invitations,
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
        { member: 'alice@example.com', joinedAt: new Date('2026-01-01') },
        { member: 'bob@example.com', joinedAt: new Date('2026-02-01') },
      ]);
      expect(client.getQueryData(groupMembersQueryKey('group-1'))).toEqual(vm.members());
    });
    it('rejects a missing group without requesting members or inviting', async () => {
      const vm = create(null);
      await vm.invite('alice@example.com');
      expect(vm.hasLoadError()).toBe(true);
      expect(vm.errorMessage()).toBe('errors.groups.noneSelected');
      expect({ reads: members.requests, writes: invitations.commands }).toEqual({
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
  describe('invite', () => {
    it.each(['', 'bad', 'a@@example.com', 'a b@example.com'])(
      'rejects invalid email %s',
      async (email) => {
        const vm = create();
        await ready(vm);
        await vm.invite(email);
        expect(vm.invitationError()).toBe('validation.members.email');
        expect(invitations.commands).toEqual([]);
      },
    );
    it('normalizes the email and confirms an invitation without adding a member', async () => {
      const vm = create();
      await ready(vm);
      await vm.invite('  CHARLIE@Example.com  ');
      expect(invitations.commands).toEqual([{ groupId: 'group-1', email: 'charlie@example.com' }]);
      expect(vm.invitedEmail()).toBe('charlie@example.com');
      expect(vm.members().map((member) => member.member)).toEqual([
        'alice@example.com',
        'bob@example.com',
      ]);
      expect(client.getQueryState(groupMembersQueryKey('group-1'))?.isInvalidated).toBe(true);
    });
    it('prevents duplicate submissions while pending', async () => {
      let resolve!: () => void;
      invitations.pending = new Promise((done) => {
        resolve = done;
      });
      const vm = create();
      await ready(vm);
      const request = vm.invite('charlie@example.com');
      const duplicate = vm.invite('dana@example.com');
      await Promise.resolve();
      await Promise.resolve();
      expect(invitations.commands).toEqual([{ groupId: 'group-1', email: 'charlie@example.com' }]);
      resolve();
      await Promise.all([request, duplicate]);
    });
    it('clears a previous success on failure and allows retry', async () => {
      const vm = create();
      await ready(vm);
      await vm.invite('charlie@example.com');
      invitations.pending = Promise.reject(new Error('failed'));
      await vm.invite('dana@example.com');
      expect(vm.invitedEmail()).toBeNull();
      expect(vm.invitationError()).toBe('failed');
      expect(vm.isInviting()).toBe(false);
      invitations.pending = Promise.resolve();
      await vm.invite('dana@example.com');
      expect(vm.invitedEmail()).toBe('dana@example.com');
    });
  });
});
