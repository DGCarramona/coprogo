package tech.justdev.infrastructure.document.s3

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import tech.justdev.application.document.DocumentDownloadRequest
import tech.justdev.application.document.DocumentFileName
import tech.justdev.application.document.DocumentMediaType
import tech.justdev.application.document.DocumentSha256
import tech.justdev.application.document.DocumentSize
import tech.justdev.application.document.DocumentStorageKey
import tech.justdev.application.document.DocumentUploadRequest
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Base64

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3DocumentStorageTest {
    private val endpoint = URI.create("http://localhost:9090")
    private lateinit var client: S3AsyncClient
    private lateinit var presigner: S3Presigner
    private lateinit var storage: S3DocumentStorage

    @BeforeAll
    fun setUp() {
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
        presigner.close()
        client.close()
    }

    @Nested
    inner class PresignUpload {
        @Test
        fun `should bind upload constraints and prevent overwrite`() =
            runTest {
                val earliestExpiration = Instant.now().plus(Duration.ofMinutes(5)).minusSeconds(1)
                val target = storage.presignUpload(uploadRequest())

                assertEquals("/coprogo-documents/documents/invoice.pdf", target.uri.path)
                assertEquals("application/pdf", target.requiredHeaders.getValue("content-type"))
                assertEquals(CHECKSUM, target.requiredHeaders.getValue("x-amz-checksum-sha256"))
                assertEquals("*", target.requiredHeaders.getValue("if-none-match"))
                assertEquals(false, target.requiredHeaders.containsKey("content-length"))
                assertTrue(target.expiresAt >= earliestExpiration)
                assertTrue(target.expiresAt <= Instant.now().plus(Duration.ofMinutes(5)).plusSeconds(1))
            }
    }

    @Nested
    inner class PresignDownload {
        @Test
        fun `should bind the requested download file name`() =
            runTest {
                val earliestExpiration = Instant.now().plus(Duration.ofMinutes(5)).minusSeconds(1)
                val target =
                    storage.presignDownload(
                        DocumentDownloadRequest(
                            key = DocumentStorageKey.of("documents/invoice.pdf"),
                            fileName = DocumentFileName.of("Facture été.pdf"),
                            validFor = Duration.ofMinutes(5),
                        ),
                    )

                assertTrue(target.uri.rawQuery.contains("response-content-disposition"))
                assertTrue(target.uri.rawQuery.contains("Facture%2520%25C3%25A9t%25C3%25A9.pdf"))
                assertTrue(target.expiresAt >= earliestExpiration)
                assertTrue(target.expiresAt <= Instant.now().plus(Duration.ofMinutes(5)).plusSeconds(1))
            }
    }

    private fun uploadRequest(): DocumentUploadRequest =
        DocumentUploadRequest(
            key = DocumentStorageKey.of("documents/invoice.pdf"),
            mediaType = DocumentMediaType.of("application/pdf"),
            size = DocumentSize.ofBytes(4),
            checksum = DocumentSha256.fromBase64(CHECKSUM),
            validFor = Duration.ofMinutes(5),
        )

    companion object {
        private const val BUCKET = "coprogo-documents"
        private val CHECKSUM = Base64.getEncoder().encodeToString(ByteArray(32))
    }
}
