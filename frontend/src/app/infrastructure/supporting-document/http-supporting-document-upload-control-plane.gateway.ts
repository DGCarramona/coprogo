import { Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  SupportingDocumentUploadControlPlanePort,
  type StartSupportingDocumentUploadCommand,
  type SupportingDocumentUploadTarget,
} from '../../application/supporting-document/supporting-document-upload.port';
import { SupportingDocumentUploadsService } from '../api/generated';
import { toApiClientError } from '../api/api-client.error';

@Injectable({ providedIn: 'root' })
export class HttpSupportingDocumentUploadControlPlaneGateway extends SupportingDocumentUploadControlPlanePort {
  constructor(private readonly supportingDocumentUploadsService: SupportingDocumentUploadsService) {
    super();
  }

  override async start(
    command: StartSupportingDocumentUploadCommand,
  ): Promise<SupportingDocumentUploadTarget> {
    return await firstValueFrom(
      this.supportingDocumentUploadsService
        .startUpload(command.groupId, {
          fileName: command.fileName,
          mediaType: command.mediaType,
          sizeBytes: command.sizeBytes,
          sha256: command.sha256,
        })
        .pipe(
          catchError((error) => {
            throw toApiClientError(error, 'errors.documents.startUpload');
          }),
          map(({ intentId, uploadUrl, requiredHeaders }): SupportingDocumentUploadTarget => ({
            intentId,
            uploadUrl,
            requiredHeaders,
          })),
        ),
    );
  }

  override async confirm(groupId: string, intentId: string): Promise<void> {
    await firstValueFrom(
      this.supportingDocumentUploadsService.confirmUpload(groupId, intentId).pipe(
        catchError((error) => {
          throw toApiClientError(error, 'errors.documents.confirmUpload');
        }),
        map(() => undefined),
      ),
    );
  }
}
