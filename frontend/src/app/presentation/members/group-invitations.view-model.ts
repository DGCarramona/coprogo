import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { GroupInvitationsPort } from '../../application/group/group-invitations.port';
import { describeError } from '../../application/shared/describe-error';

export const groupInvitationsQueryKey = (groupId: string | null) =>
  ['groups', groupId, 'sent-invitations'] as const;

@Injectable()
export class GroupInvitationsViewModel {
  private readonly group = signal<string | null>(null);
  private readonly query;
  private readonly mutation;
  private readonly cancelling = signal(false);
  private readonly error = signal<string | null>(null);
  readonly cancelError = this.error.asReadonly();
  readonly isCancelling = this.cancelling.asReadonly();
  readonly invitations = computed(() => this.query.data() ?? []);
  readonly isLoading = computed(() => this.query.isPending());
  readonly hasLoadError = computed(() => this.query.isError());
  readonly loadError = computed(() =>
    describeError(this.query.error(), 'errors.members.loadInvitations'),
  );

  constructor(
    port: GroupInvitationsPort,
    private readonly client: QueryClient,
    injector: Injector,
  ) {
    this.query = injectQuery(
      () => ({
        queryKey: groupInvitationsQueryKey(this.group()),
        queryFn: () => port.list(this.group()!),
        enabled: this.group() !== null,
      }),
      { injector },
    );
    this.mutation = injectMutation<void, Error, { group: string; invitation: string }>(
      () => ({
        mutationFn: ({ group, invitation }) => port.cancel(group, invitation),
      }),
      { injector },
    );
  }

  setGroup(groupId: string): void {
    this.group.set(groupId);
  }
  retry(): void {
    void this.query.refetch();
  }

  async cancel(invitation: string): Promise<void> {
    const group = this.group();
    if (
      group === null ||
      this.cancelling() ||
      !this.invitations().some((row) => row.invitation === invitation && row.canCancel)
    ) {
      return;
    }
    this.cancelling.set(true);
    this.error.set(null);
    try {
      await this.mutation.mutateAsync({ group, invitation });
      await this.client.invalidateQueries({ queryKey: groupInvitationsQueryKey(group) });
    } catch (error: unknown) {
      this.error.set(describeError(error, 'errors.members.cancelInvitation'));
      await this.client.invalidateQueries({ queryKey: groupInvitationsQueryKey(group) });
    } finally {
      this.cancelling.set(false);
    }
  }
}
