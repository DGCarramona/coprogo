import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { GroupCreationPort } from '../../application/group/group-creation.port';
import { toApiClientError } from '../api/api-client.error';
import { API_BASE_PATH } from '../api/provide-api-client';

interface CreateGroupResponseDto {
  group: string;
}

@Injectable({ providedIn: 'root' })
export class HttpGroupCreationGateway extends GroupCreationPort {
  constructor(
    private readonly httpClient: HttpClient,
    @Inject(API_BASE_PATH) private readonly basePath: string,
  ) {
    super();
  }

  override async create(): Promise<string> {
    try {
      const response = await firstValueFrom(
        this.httpClient.post<CreateGroupResponseDto>(`${this.basePath}/api/groups`, {}),
      );

      return response.group;
    } catch (error) {
      throw toApiClientError(error, 'Le groupe n a pas pu etre cree.');
    }
  }
}
