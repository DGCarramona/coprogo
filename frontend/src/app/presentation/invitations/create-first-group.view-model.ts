import { Injectable, signal } from '@angular/core';

import { GroupCreationPort } from '../../application/group/group-creation.port';
import { describeError } from '../../application/shared/describe-error';

@Injectable()
export class CreateFirstGroupViewModel {
  private readonly creatingGroupState = signal(false);
  private readonly createdGroupIdState = signal<string | null>(null);
  private readonly errorMessageState = signal<string | null>(null);

  constructor(private readonly groupCreationPort: GroupCreationPort) {}

  isCreatingGroup() {
    return this.creatingGroupState();
  }

  createdGroupId() {
    return this.createdGroupIdState();
  }

  errorMessage() {
    return this.errorMessageState();
  }

  async createGroup(): Promise<void> {
    this.creatingGroupState.set(true);
    this.errorMessageState.set(null);
    this.createdGroupIdState.set(null);

    try {
      this.createdGroupIdState.set(await this.groupCreationPort.create());
    } catch (error) {
      this.errorMessageState.set(describeError(error, 'Le groupe n a pas pu etre cree.'));
    } finally {
      this.creatingGroupState.set(false);
    }
  }
}
