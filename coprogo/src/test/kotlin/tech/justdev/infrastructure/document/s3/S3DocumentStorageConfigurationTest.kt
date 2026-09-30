package tech.justdev.infrastructure.document.s3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI

class S3DocumentStorageConfigurationTest {
    @Nested
    inner class Validate {
        @Test
        fun `should normalize a complete configuration`() {
            val configuration =
                S3DocumentStorageConfiguration().apply {
                    bucket = "  coprogo-documents  "
                    region = "  eu-west-1  "
                    endpoint = URI.create("http://localhost:9090")
                    pathStyleAccess = true
                }

            configuration.validate()

            assertEquals("coprogo-documents", configuration.bucket)
            assertEquals("eu-west-1", configuration.region)
            assertEquals(URI.create("http://localhost:9090"), configuration.endpoint)
            assertEquals(true, configuration.pathStyleAccess)
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
            "blank bucket, '', eu-west-1",
            "blank region, coprogo-documents, ' '",
        )
        fun `should reject a blank bucket or region`(
            caseName: String,
            bucket: String,
            region: String,
        ) {
            val configuration =
                S3DocumentStorageConfiguration().apply {
                    this.bucket = bucket
                    this.region = region
                }

            assertThrows<IllegalArgumentException> { configuration.validate() }
        }

        @Test
        fun `should reject a non HTTP endpoint`() {
            val configuration =
                S3DocumentStorageConfiguration().apply {
                    bucket = "coprogo-documents"
                    region = "eu-west-1"
                    endpoint = URI.create("file:///tmp/s3")
                }

            val error = assertThrows<IllegalArgumentException> { configuration.validate() }

            assertEquals("coprogo.documents.s3.endpoint must use HTTP or HTTPS", error.message)
        }
    }
}
