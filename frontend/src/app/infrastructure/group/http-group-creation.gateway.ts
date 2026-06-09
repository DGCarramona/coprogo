import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { GroupCreationPort } from '../../application/group/group-creation.port';
import { toApiClientError } from '../api/api-client.error';
import { API_BASE_PATH } from '../api/provide-api-client';

interface CreateGroupResponseDto {
  group: string;
}

@Injectable({ providedIn: 'root' })
export class HttpGroupCreationGateway extends GroupCreationPort {
  private readonly httpClient = inject(HttpClient);
  private readonly basePath = inject(API_BASE_PATH);

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
