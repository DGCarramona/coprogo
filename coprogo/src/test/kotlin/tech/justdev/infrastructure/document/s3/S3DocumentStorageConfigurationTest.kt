package tech.justdev.infrastructure.document.s3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

        @Test
        fun `should reject a blank bucket or region`() {
            listOf("" to "eu-west-1", "coprogo-documents" to " ").forEach { (bucket, region) ->
                val configuration =
                    S3DocumentStorageConfiguration().apply {
                        this.bucket = bucket
                        this.region = region
                    }

                assertThrows<IllegalArgumentException> { configuration.validate() }
            }
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
