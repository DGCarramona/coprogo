import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { catchError, firstValueFrom, map } from 'rxjs';

import {
  SignedSupportingDocumentUploaderPort,
  type SupportingDocumentUploadTarget,
} from '../../application/supporting-document/supporting-document-upload.port';
import { toApiClientError } from '../api/api-client.error';
import { DIRECT_HTTP_CLIENT } from '../http/named-http-clients';

@Injectable({ providedIn: 'root' })
export class HttpSignedSupportingDocumentUploaderGateway extends SignedSupportingDocumentUploaderPort {
  constructor(@Inject(DIRECT_HTTP_CLIENT) private readonly httpClient: HttpClient) {
    super();
  }

  override async upload(target: SupportingDocumentUploadTarget, bytes: Uint8Array): Promise<void> {
    await firstValueFrom(
      this.httpClient
        .put(target.uploadUrl, bytes, {
          headers: new HttpHeaders(target.requiredHeaders),
        })
        .pipe(
          catchError((error) => {
            throw toApiClientError(error, 'Le televersement direct du justificatif a echoue.');
          }),
          map(() => undefined),
        ),
    );
  }
}
