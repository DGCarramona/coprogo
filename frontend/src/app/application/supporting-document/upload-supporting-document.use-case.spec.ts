import {
  Sha256ChecksumPort,
  SignedSupportingDocumentUploaderPort,
  SupportingDocumentUploadControlPlanePort,
  type StartSupportingDocumentUploadCommand,
  type SupportingDocumentUploadTarget,
} from './supporting-document-upload.port';
import { UploadSupportingDocument } from './upload-supporting-document.use-case';

describe('UploadSupportingDocument', () => {
  describe('upload', () => {
    it('hashes, uploads and confirms a document before returning its intent identifier', async () => {
      const operations: string[] = [];
      const useCase = new UploadSupportingDocument(
        new StubSha256ChecksumPort(operations),
        new StubSupportingDocumentUploadControlPlanePort(operations),
        new StubSignedSupportingDocumentUploaderPort(operations),
      );

      await expect(
        useCase.upload('c4276b6a-76f1-42d5-997a-bae590e8d137', {
          fileName: 'facture.pdf',
          mediaType: 'application/pdf',
          bytes: new Uint8Array([1, 2, 3]),
        }),
      ).resolves.toBe('e6371f49-61f3-4f2e-8d66-3af4201c8a3e');

      expect(operations).toEqual(['checksum', 'start', 'upload', 'confirm']);
      expect(StubSupportingDocumentUploadControlPlanePort.startedCommands).toEqual([
        {
          groupId: 'c4276b6a-76f1-42d5-997a-bae590e8d137',
          fileName: 'facture.pdf',
          mediaType: 'application/pdf',
          sizeBytes: 3,
          sha256: 'checksum-base64',
        },
      ]);
      expect(StubSignedSupportingDocumentUploaderPort.uploads).toEqual([
        {
          target: {
            intentId: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
            uploadUrl: 'https://documents.example/upload',
            requiredHeaders: { 'Content-Type': 'application/pdf' },
          },
          bytes: new Uint8Array([1, 2, 3]),
        },
      ]);
      expect(StubSupportingDocumentUploadControlPlanePort.confirmedIntents).toEqual([
        {
          groupId: 'c4276b6a-76f1-42d5-997a-bae590e8d137',
          intentId: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
        },
      ]);
    });

    it('does not confirm the intent when the direct upload fails', async () => {
      const operations: string[] = [];
      const useCase = new UploadSupportingDocument(
        new StubSha256ChecksumPort(operations),
        new StubSupportingDocumentUploadControlPlanePort(operations),
        new FailingSignedSupportingDocumentUploaderPort(operations),
      );

      await expect(
        useCase.upload('c4276b6a-76f1-42d5-997a-bae590e8d137', {
          fileName: 'facture.pdf',
          mediaType: 'application/pdf',
          bytes: new Uint8Array([1, 2, 3]),
        }),
      ).rejects.toThrow('Le televersement direct a echoue.');

      expect(operations).toEqual(['checksum', 'start', 'upload']);
      expect(StubSupportingDocumentUploadControlPlanePort.confirmedIntents).toEqual([]);
    });
  });
});

class StubSha256ChecksumPort extends Sha256ChecksumPort {
  constructor(private readonly operations: string[]) {
    super();
  }

  override async checksum(): Promise<string> {
    this.operations.push('checksum');
    return 'checksum-base64';
  }
}

class StubSupportingDocumentUploadControlPlanePort extends SupportingDocumentUploadControlPlanePort {
  static startedCommands: StartSupportingDocumentUploadCommand[] = [];
  static confirmedIntents: { groupId: string; intentId: string }[] = [];

  constructor(private readonly operations: string[]) {
    super();
    StubSupportingDocumentUploadControlPlanePort.startedCommands = [];
    StubSupportingDocumentUploadControlPlanePort.confirmedIntents = [];
  }

  override async start(
    command: StartSupportingDocumentUploadCommand,
  ): Promise<SupportingDocumentUploadTarget> {
    this.operations.push('start');
    StubSupportingDocumentUploadControlPlanePort.startedCommands.push(command);

    return {
      intentId: 'e6371f49-61f3-4f2e-8d66-3af4201c8a3e',
      uploadUrl: 'https://documents.example/upload',
      requiredHeaders: { 'Content-Type': 'application/pdf' },
    };
  }

  override async confirm(groupId: string, intentId: string): Promise<void> {
    this.operations.push('confirm');
    StubSupportingDocumentUploadControlPlanePort.confirmedIntents.push({ groupId, intentId });
  }
}

class StubSignedSupportingDocumentUploaderPort extends SignedSupportingDocumentUploaderPort {
  static uploads: { target: SupportingDocumentUploadTarget; bytes: Uint8Array }[] = [];

  constructor(private readonly operations: string[]) {
    super();
    StubSignedSupportingDocumentUploaderPort.uploads = [];
  }

  override async upload(target: SupportingDocumentUploadTarget, bytes: Uint8Array): Promise<void> {
    this.operations.push('upload');
    StubSignedSupportingDocumentUploaderPort.uploads.push({ target, bytes });
  }
}

class FailingSignedSupportingDocumentUploaderPort extends SignedSupportingDocumentUploaderPort {
  constructor(private readonly operations: string[]) {
    super();
  }

  override async upload(): Promise<void> {
    this.operations.push('upload');
    throw new Error('Le televersement direct a echoue.');
  }
}
