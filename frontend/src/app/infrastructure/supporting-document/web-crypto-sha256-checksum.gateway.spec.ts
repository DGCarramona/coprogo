import { WebCryptoSha256ChecksumGateway } from './web-crypto-sha256-checksum.gateway';

describe('WebCryptoSha256ChecksumGateway', () => {
  describe('checksum', () => {
    it('returns the canonical base64 SHA-256 digest of the supplied bytes', async () => {
      await expect(
        new WebCryptoSha256ChecksumGateway().checksum(new TextEncoder().encode('abc')),
      ).resolves.toBe('ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=');
    });
  });
});
