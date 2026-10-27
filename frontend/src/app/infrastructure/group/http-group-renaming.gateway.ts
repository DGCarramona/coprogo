import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map, throwError } from 'rxjs';
import { GroupRenamingPort } from '../../application/group/group-renaming.port';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupRenamingGateway extends GroupRenamingPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override rename(groupId: string, name: string): Promise<void> {
    return firstValueFrom(
      this.groupsService.renameGroup(groupId, { name }).pipe(
        catchError((error: unknown) =>
          throwError(() => toApiClientError(error, 'Le groupe n’a pas pu être renommé.')),
        ),
        map(() => undefined),
      ),
    );
  }
}
