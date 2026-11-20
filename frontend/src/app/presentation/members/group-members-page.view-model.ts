import { computed, Injectable, Injector, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { GroupMemberAdditionPort } from '../../application/group/group-member-addition.port';
import { GroupMembersPort } from '../../application/group/group-members.port';
import { describeError } from '../../application/shared/describe-error';
import { MemberEmail } from '../../domain/group/member-email';
import {
  groupMembersQueryKey,
  groupMembersQueryOptions,
} from '../shared/group-members/group-members-query';

@Injectable()
export class GroupMembersPageViewModel {
  readonly groupId: string | null;
  private readonly membersQuery;
  private readonly addMemberMutation;
  private addMemberInFlight = false;
  private readonly addMemberErrorState = signal<string | null>(null);
  private readonly addedMemberState = signal<MemberEmail | null>(null);
  readonly addMemberError = this.addMemberErrorState.asReadonly();
  readonly addedMember = this.addedMemberState.asReadonly();
  readonly members = computed(() => this.membersQuery.data() ?? []);
  readonly isLoading = computed(() => this.groupId !== null && this.membersQuery.isPending());
  readonly hasLoadError = computed(() => this.groupId === null || this.membersQuery.isError());
  readonly isReady = computed(() => this.groupId !== null && this.membersQuery.isSuccess());
  readonly isAddingMember = computed(() => this.addMemberMutation.isPending());
  readonly errorMessage = computed(() =>
    this.groupId === null
      ? 'errors.groups.noneSelected'
      : describeError(this.membersQuery.error(), 'errors.groups.loadMembers'),
  );

  constructor(
    route: ActivatedRoute,
    membersPort: GroupMembersPort,
    memberAdditionPort: GroupMemberAdditionPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    this.membersQuery = injectQuery(
      () => ({
        ...groupMembersQueryOptions(this.groupId, membersPort),
        select: (members) => members,
      }),
      { injector },
    );
    this.addMemberMutation = injectMutation<void, Error, { groupId: string; email: MemberEmail }>(
      () => ({ mutationFn: ({ groupId, email }) => memberAdditionPort.addMember(groupId, email) }),
      { injector },
    );
  }

  async addMember(rawEmail: string): Promise<void> {
    if (this.groupId === null || !this.isReady() || this.addMemberInFlight) return;
    this.addedMemberState.set(null);
    this.addMemberErrorState.set(null);
    this.addMemberInFlight = true;
    try {
      const email = MemberEmail(rawEmail);
      await this.addMemberMutation.mutateAsync({ groupId: this.groupId, email });
      await this.queryClient.invalidateQueries({
        queryKey: groupMembersQueryKey(this.groupId),
        refetchType: 'none',
      });
      await this.membersQuery.refetch();
      this.addedMemberState.set(email);
    } catch (error: unknown) {
      this.addMemberErrorState.set(describeError(error, 'errors.members.add'));
    } finally {
      this.addMemberInFlight = false;
    }
  }

  retry(): void {
    if (this.groupId !== null) void this.membersQuery.refetch();
  }
}
