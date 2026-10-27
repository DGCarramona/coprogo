import { Injectable, signal } from '@angular/core';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { memberGroupsQueryKey } from './member-groups-query';

import { GroupCreationPort } from '../../application/group/group-creation.port';
import { describeError } from '../../application/shared/describe-error';
import { parseGroupName } from '../../domain/group/group-name';

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
    const name = parseGroupName(rawName);
    if (name === null) {
      this.errorMessageState.set('Indiquez un nom de groupe de 1 à 100 caractères.');
      return;
    }
    if (this.creatingGroupState()) return;
    this.creatingGroupState.set(true);
    this.errorMessageState.set(null);
    this.createdGroupIdState.set(null);

    try {
      this.createdGroupIdState.set(await this.groupCreationPort.create(name));
      await this.queryClient.invalidateQueries({ queryKey: memberGroupsQueryKey });
    } catch (error) {
      this.errorMessageState.set(describeError(error, 'Le groupe n a pas pu etre cree.'));
    } finally {
      this.creatingGroupState.set(false);
    }
  }
}
