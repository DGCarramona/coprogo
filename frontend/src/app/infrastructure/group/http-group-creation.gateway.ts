import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import { GroupCreationPort } from '../../application/group/group-creation.port';
import { GroupsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpGroupCreationGateway extends GroupCreationPort {
  constructor(private readonly groupsService: GroupsService) {
    super();
  }

  override async create(): Promise<string> {
    return await firstValueFrom(
      this.groupsService.create().pipe(
        catchError((error) => {
          throw toApiClientError(error, 'Le groupe n a pas pu etre cree.');
        }),
        map((response) => response.group),
      ),
    );
  }
}
