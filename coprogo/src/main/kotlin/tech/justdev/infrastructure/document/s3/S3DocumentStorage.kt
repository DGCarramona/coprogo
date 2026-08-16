package tech.justdev.infrastructure.document.s3

import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.ChecksumMode
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentDownloadTarget
import tech.justdev.application.document.DocumentStorage
import tech.justdev.application.document.DocumentUploadRequest
import tech.justdev.application.document.DocumentUploadTarget
import tech.justdev.domain.document.entity.DocumentMetadata
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import java.nio.charset.StandardCharsets.UTF_8

class S3DocumentStorage(
    private val client: S3AsyncClient,
    private val presigner: S3Presigner,
    private val bucket: String,
) : DocumentStorage {
    override suspend fun presignUpload(request: DocumentUploadRequest): DocumentUploadTarget {
        val presigned =
            presigner.presignPutObject(
                PutObjectPresignRequest
                    .builder()
                    .signatureDuration(request.validFor)
                    .putObjectRequest(
                        PutObjectRequest
                            .builder()
                            .bucket(bucket)
                            .key(request.key.toPrimitive())
                            .contentType(request.mediaType.toPrimitive())
                            .checksumSHA256(request.checksum.toBase64())
                            .ifNoneMatch("*")
                            .build(),
                    ).build(),
            )

        return DocumentUploadTarget(
            uri = presigned.url().toURI(),
            requiredHeaders =
                presigned
                    .signedHeaders()
                    .filterKeys { header -> !header.equals("host", ignoreCase = true) }
                    .mapKeys { (header) -> header.lowercase() }
                    .mapValues { (_, values) -> values.joinToString(",") },
            expiresAt = presigned.expiration(),
        )
    }

    override suspend fun inspect(key: DocumentStorageKey): DocumentMetadata? =
        try {
            val response =
                client
                    .headObject(
                        HeadObjectRequest
                            .builder()
                            .bucket(bucket)
                            .key(key.toPrimitive())
                            .checksumMode(ChecksumMode.ENABLED)
                            .build(),
                    ).await()

            DocumentMetadata(
                mediaType = DocumentMediaType.of(requireNotNull(response.contentType()) { "stored document media type is missing" }),
                size = DocumentSize.ofBytes(response.contentLength()),
                checksum = DocumentSha256.fromBase64(requireNotNull(response.checksumSHA256()) { "stored document checksum is missing" }),
            )
        } catch (error: S3Exception) {
            if (error.statusCode() == 404) null else throw error
        }

    override suspend fun presignDownload(request: DocumentDownloadRequest): DocumentDownloadTarget {
        val presigned =
            presigner.presignGetObject(
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(request.validFor)
                    .getObjectRequest(
                        GetObjectRequest
                            .builder()
                            .bucket(bucket)
                            .key(request.key.toPrimitive())
                            .responseContentDisposition(request.fileName.asContentDisposition())
                            .build(),
                    ).build(),
            )

        return DocumentDownloadTarget(
            uri = presigned.url().toURI(),
            expiresAt = presigned.expiration(),
        )
    }
}

private fun DocumentFileName.asContentDisposition(): String = "attachment; filename*=UTF-8''${toPrimitive().encodeRfc5987()}"

private fun String.encodeRfc5987(): String =
    toByteArray(UTF_8).joinToString(separator = "") { byte ->
        val unsigned = byte.toInt() and 0xff
        if (unsigned.isRfc5987AttributeCharacter()) unsigned.toChar().toString() else "%%%02X".format(unsigned)
    }

private fun Int.isRfc5987AttributeCharacter(): Boolean =
    this in 'a'.code..'z'.code ||
        this in 'A'.code..'Z'.code ||
        this in '0'.code..'9'.code ||
        this in "!#$&+-.^_`|~".map(Char::code)
