export interface SupportingDocumentFile {
  readonly fileName: string;
  readonly mediaType: string;
  readonly bytes: Uint8Array;
}

export interface StartSupportingDocumentUploadCommand {
  readonly groupId: string;
  readonly fileName: string;
  readonly mediaType: string;
  readonly sizeBytes: number;
  readonly sha256: string;
}

export interface SupportingDocumentUploadTarget {
  readonly intentId: string;
  readonly uploadUrl: string;
  readonly requiredHeaders: Readonly<Record<string, string>>;
}

export abstract class Sha256ChecksumPort {
  abstract checksum(bytes: Uint8Array): Promise<string>;
}

export abstract class SupportingDocumentUploadControlPlanePort {
  abstract start(
    command: StartSupportingDocumentUploadCommand,
  ): Promise<SupportingDocumentUploadTarget>;

  abstract confirm(groupId: string, intentId: string): Promise<void>;
}

export abstract class SignedSupportingDocumentUploaderPort {
  abstract upload(target: SupportingDocumentUploadTarget, bytes: Uint8Array): Promise<void>;
}
