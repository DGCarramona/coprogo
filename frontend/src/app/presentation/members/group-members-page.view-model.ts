import { computed, Injectable, Injector, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { GroupMemberInvitationPort } from '../../application/group/group-member-invitation.port';
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
  private readonly invitationMutation;
  private invitationInFlight = false;
  private readonly invitationErrorState = signal<string | null>(null);
  private readonly invitedEmailState = signal<MemberEmail | null>(null);
  readonly invitationError = this.invitationErrorState.asReadonly();
  readonly invitedEmail = this.invitedEmailState.asReadonly();
  readonly members = computed(() => this.membersQuery.data() ?? []);
  readonly isLoading = computed(() => this.groupId !== null && this.membersQuery.isPending());
  readonly hasLoadError = computed(() => this.groupId === null || this.membersQuery.isError());
  readonly isReady = computed(() => this.groupId !== null && this.membersQuery.isSuccess());
  readonly isInviting = computed(() => this.invitationMutation.isPending());
  readonly errorMessage = computed(() =>
    this.groupId === null
      ? 'errors.groups.noneSelected'
      : describeError(this.membersQuery.error(), 'errors.groups.loadMembers'),
  );

  constructor(
    route: ActivatedRoute,
    membersPort: GroupMembersPort,
    invitationPort: GroupMemberInvitationPort,
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
    this.invitationMutation = injectMutation<void, Error, { groupId: string; email: MemberEmail }>(
      () => ({ mutationFn: ({ groupId, email }) => invitationPort.invite(groupId, email) }),
      { injector },
    );
  }

  async invite(rawEmail: string): Promise<void> {
    if (this.groupId === null || !this.isReady() || this.invitationInFlight) return;
    this.invitedEmailState.set(null);
    this.invitationErrorState.set(null);
    this.invitationInFlight = true;
    try {
      const email = MemberEmail(rawEmail);
      await this.invitationMutation.mutateAsync({ groupId: this.groupId, email });
      await this.queryClient.invalidateQueries({
        queryKey: groupMembersQueryKey(this.groupId),
        refetchType: 'none',
      });
      this.invitedEmailState.set(email);
    } catch (error: unknown) {
      this.invitationErrorState.set(describeError(error, 'errors.members.invite'));
    } finally {
      this.invitationInFlight = false;
    }
  }

  retry(): void {
    if (this.groupId !== null) void this.membersQuery.refetch();
  }
}
