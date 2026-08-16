package tech.justdev.infrastructure.document.s3

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentUploadRequest
import tech.justdev.domain.document.valueobject.DocumentFileName
import tech.justdev.domain.document.valueobject.DocumentMediaType
import tech.justdev.domain.document.valueobject.DocumentSha256
import tech.justdev.domain.document.valueobject.DocumentSize
import tech.justdev.domain.document.valueobject.DocumentStorageKey
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.UUID

/**
 * S3Mock proves S3 protocol compatibility and round trips. It accepts presigned URLs but deliberately
 * does not validate their signature, expiration, or HTTP verb; signed-header binding is covered by
 * [S3DocumentStorageTest].
 */
@EnabledIfEnvironmentVariable(named = "S3MOCK_ENDPOINT", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3DocumentStorageIntegrationTest {
    private lateinit var client: S3AsyncClient
    private lateinit var presigner: S3Presigner
    private lateinit var storage: S3DocumentStorage
    private val httpClient = HttpClient.newHttpClient()
    private val createdKeys = mutableSetOf<DocumentStorageKey>()

    @BeforeAll
    fun setUp() {
        val endpoint = URI.create(System.getenv("S3MOCK_ENDPOINT"))
        val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
        val serviceConfiguration = S3Configuration.builder().pathStyleAccessEnabled(true).build()
        client =
            S3AsyncClient
                .builder()
                .credentialsProvider(credentials)
                .region(Region.EU_WEST_1)
                .endpointOverride(endpoint)
                .serviceConfiguration(serviceConfiguration)
                .build()
        presigner =
            S3Presigner
                .builder()
                .credentialsProvider(credentials)
                .region(Region.EU_WEST_1)
                .endpointOverride(endpoint)
                .serviceConfiguration(serviceConfiguration)
                .build()
        storage = S3DocumentStorage(client, presigner, BUCKET)
    }

    @AfterAll
    fun tearDown() {
        runBlocking {
            createdKeys.forEach { key ->
                client
                    .deleteObject(
                        DeleteObjectRequest
                            .builder()
                            .bucket(BUCKET)
                            .key(key.toPrimitive())
                            .build(),
                    ).await()
            }
        }
        presigner.close()
        client.close()
    }

    @Nested
    inner class PresignUpload {
        @Test
        fun `should upload through the signed target`() =
            runTest {
                val bytes = "invoice".toByteArray()
                val key = uniqueKey()
                val target = storage.presignUpload(uploadRequest(key, bytes))
                val builder =
                    HttpRequest
                        .newBuilder(target.uri)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                target.requiredHeaders.forEach(builder::header)

                val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding())

                assertEquals(200, response.statusCode())
                assertEquals(bytes.size.toLong(), storage.inspect(key)?.size?.toBytes())
            }
    }

    @Nested
    inner class Inspect {
        @Test
        fun `should map stored metadata and return null when absent`() =
            runTest {
                val bytes = "stored invoice".toByteArray()
                val key = uniqueKey()
                val checksum = sha256(bytes)
                client
                    .putObject(
                        PutObjectRequest
                            .builder()
                            .bucket(BUCKET)
                            .key(key.toPrimitive())
                            .contentType("application/pdf")
                            .checksumSHA256(checksum)
                            .build(),
                        AsyncRequestBody.fromBytes(bytes),
                    ).await()

                val metadata = storage.inspect(key)

                assertEquals("application/pdf", metadata?.mediaType?.toPrimitive())
                assertEquals(bytes.size.toLong(), metadata?.size?.toBytes())
                assertEquals(checksum, metadata?.checksum?.toBase64())
                assertEquals(null, storage.inspect(uniqueKey()))
            }
    }

    @Nested
    inner class PresignDownload {
        @Test
        fun `should download stored bytes through the signed target`() =
            runTest {
                val bytes = "downloaded invoice".toByteArray()
                val key = uniqueKey()
                putObject(key, bytes)
                val target =
                    storage.presignDownload(
                        DocumentDownloadRequest(
                            key = key,
                            fileName = DocumentFileName.of("invoice.pdf"),
                            validFor = Duration.ofMinutes(5),
                        ),
                    )

                val response =
                    httpClient.send(
                        HttpRequest.newBuilder(target.uri).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray(),
                    )

                assertEquals(200, response.statusCode())
                assertArrayEquals(bytes, response.body())
            }
    }

    private suspend fun putObject(
        key: DocumentStorageKey,
        bytes: ByteArray,
    ) {
        client
            .putObject(
                PutObjectRequest
                    .builder()
                    .bucket(BUCKET)
                    .key(key.toPrimitive())
                    .contentType("application/pdf")
                    .checksumSHA256(sha256(bytes))
                    .build(),
                AsyncRequestBody.fromBytes(bytes),
            ).await()
    }

    private fun uploadRequest(
        key: DocumentStorageKey,
        bytes: ByteArray,
    ): DocumentUploadRequest =
        DocumentUploadRequest(
            key = key,
            mediaType = DocumentMediaType.of("application/pdf"),
            size = DocumentSize.ofBytes(bytes.size.toLong()),
            checksum = DocumentSha256.fromBase64(sha256(bytes)),
            validFor = Duration.ofMinutes(5),
        )

    private fun uniqueKey(): DocumentStorageKey = DocumentStorageKey.of("tests/${UUID.randomUUID()}.pdf").also(createdKeys::add)

    private fun sha256(bytes: ByteArray): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))

    companion object {
        private const val BUCKET = "coprogo-documents"
    }
}
