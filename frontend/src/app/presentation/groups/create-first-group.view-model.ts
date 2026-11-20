import { Injectable, signal } from '@angular/core';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { memberGroupsQueryKey } from './member-groups-query';

import { GroupCreationPort } from '../../application/group/group-creation.port';
import { describeError } from '../../application/shared/describe-error';
import { GroupName } from '../../domain/group/group-name';

@Injectable()
export class CreateFirstGroupViewModel {
  private readonly creatingGroupState = signal(false);
  private readonly createdGroupIdState = signal<string | null>(null);
  private readonly errorMessageState = signal<string | null>(null);

  constructor(
    private readonly groupCreationPort: GroupCreationPort,
    private readonly queryClient: QueryClient,
  ) {}

  isCreatingGroup() {
    return this.creatingGroupState();
  }

  createdGroupId() {
    return this.createdGroupIdState();
  }

  errorMessage() {
    return this.errorMessageState();
  }

  async createGroup(rawName: string): Promise<void> {
    if (this.creatingGroupState()) return;
    try {
      const name = GroupName(rawName);
      this.creatingGroupState.set(true);
      this.errorMessageState.set(null);
      this.createdGroupIdState.set(null);
      this.createdGroupIdState.set(await this.groupCreationPort.create(name));
      await this.queryClient.invalidateQueries({ queryKey: memberGroupsQueryKey });
    } catch (error) {
      this.errorMessageState.set(describeError(error, 'errors.groups.create'));
    } finally {
      this.creatingGroupState.set(false);
    }
  }
}
