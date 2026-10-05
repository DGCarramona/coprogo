import { Injectable } from '@angular/core';

import {
  Sha256ChecksumPort,
  SignedSupportingDocumentUploaderPort,
  SupportingDocumentUploadControlPlanePort,
  type SupportingDocumentFile,
} from './supporting-document-upload.port';

@Injectable({ providedIn: 'root' })
export class UploadSupportingDocument {
  constructor(
    private readonly checksumPort: Sha256ChecksumPort,
    private readonly uploadControlPlanePort: SupportingDocumentUploadControlPlanePort,
    private readonly signedUploaderPort: SignedSupportingDocumentUploaderPort,
  ) {}

  upload(groupId: string, file: SupportingDocumentFile): Promise<string> {
    return this.checksumPort
      .checksum(file.bytes)
      .then((sha256) =>
        this.uploadControlPlanePort.start({
          groupId,
          fileName: file.fileName,
          mediaType: file.mediaType,
          sizeBytes: file.bytes.byteLength,
          sha256,
        }),
      )
      .then(tapAsync((target) => this.signedUploaderPort.upload(target, file.bytes)))
      .then(tapAsync((target) => this.uploadControlPlanePort.confirm(groupId, target.intentId)))
      .then((target) => target.intentId);
  }
}

const tapAsync =
  <T>(effect: (value: T) => Promise<unknown>) =>
  async (value: T): Promise<T> => {
    await effect(value);
    return value;
  };
