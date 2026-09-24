import { Injectable } from '@angular/core';

import { Sha256ChecksumPort } from '../../application/supporting-document/supporting-document-upload.port';

@Injectable({ providedIn: 'root' })
export class WebCryptoSha256ChecksumGateway extends Sha256ChecksumPort {
  override async checksum(bytes: Uint8Array): Promise<string> {
    return toBase64(await crypto.subtle.digest('SHA-256', toArrayBuffer(bytes)));
  }
}

const toArrayBuffer = (bytes: Uint8Array): ArrayBuffer => new Uint8Array(bytes).buffer;

const toBase64 = (digest: ArrayBuffer): string =>
  btoa(Array.from(new Uint8Array(digest), (byte) => String.fromCharCode(byte)).join(''));
