import { computed, Injectable, Injector, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { describeError } from '../../application/shared/describe-error';
import { memberGroupsQueryKey } from './member-groups-query';
import { GroupRenamingPort } from '../../application/group/group-renaming.port';
import { parseGroupName } from '../../domain/group/group-name';
import type { MemberGroup } from '../../domain/group/member-group';

@Injectable()
export class MemberGroupsViewModel {
  private readonly groupsQuery;
  private readonly renameMutation;
  private readonly editingGroupIdState = signal<string | null>(null);
  private readonly draftNameState = signal('');
  private readonly renameErrorState = signal<string | null>(null);
  readonly editingGroupId = this.editingGroupIdState.asReadonly();
  readonly draftName = this.draftNameState.asReadonly();
  readonly renameError = this.renameErrorState.asReadonly();
  readonly isSaving = computed(() => this.renameMutation.isPending());
  readonly groups = computed(() => this.groupsQuery.data() ?? []);
  readonly isLoading = computed(() => this.groupsQuery.isPending());
  readonly hasLoadError = computed(() => this.groupsQuery.isError());
  readonly errorMessage = computed(() =>
    describeError(this.groupsQuery.error(), 'Vos groupes n’ont pas pu être chargés.'),
  );

  constructor(
    port: MemberGroupsPort,
    renamingPort: GroupRenamingPort,
    private readonly queryClient: QueryClient,
    injector: Injector,
  ) {
    this.groupsQuery = injectQuery(
      () => ({
        queryKey: memberGroupsQueryKey,
        queryFn: () => port.list(),
      }),
      { injector },
    );
    this.renameMutation = injectMutation<void, Error, { groupId: string; name: string }>(
      () => ({ mutationFn: ({ groupId, name }) => renamingPort.rename(groupId, name) }),
      { injector },
    );
  }

  beginRename(group: MemberGroup): void {
    if (!group.canRename || this.isSaving()) return;
    this.editingGroupIdState.set(group.groupId);
    this.draftNameState.set(group.name ?? '');
    this.renameErrorState.set(null);
  }

  setName(name: string): void {
    this.draftNameState.set(name);
  }

  cancelRename(): void {
    if (this.isSaving()) return;
    this.editingGroupIdState.set(null);
    this.renameErrorState.set(null);
  }

  async renameGroup(): Promise<void> {
    const groupId = this.editingGroupIdState();
    if (groupId === null || this.isSaving()) return;
    const name = parseGroupName(this.draftNameState());
    if (name === null) {
      this.renameErrorState.set('Indiquez un nom de groupe de 1 à 100 caractères.');
      return;
    }
    this.renameErrorState.set(null);
    try {
      await this.renameMutation.mutateAsync({ groupId, name });
      await this.queryClient.invalidateQueries({ queryKey: memberGroupsQueryKey });
      this.editingGroupIdState.set(null);
    } catch (error: unknown) {
      this.renameErrorState.set(describeError(error, 'Le groupe n’a pas pu être renommé.'));
    }
  }

  retry(): void {
    void this.groupsQuery.refetch();
  }
}
