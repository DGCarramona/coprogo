package tech.justdev.infrastructure.document.s3

import io.micronaut.context.annotation.Property
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import tech.justdev.application.document.DocumentStorage
import tech.justdev.testsupport.NoDbMicronautTest

@NoDbMicronautTest
@Property(name = "coprogo.documents.s3.bucket", value = "coprogo-documents")
@Property(name = "coprogo.documents.s3.region", value = "eu-west-1")
@Property(name = "coprogo.documents.s3.endpoint", value = "http://localhost:9090")
@Property(name = "coprogo.documents.s3.path-style-access", value = "true")
class S3DocumentStorageWiringTest {
    @Inject
    lateinit var storage: DocumentStorage

    @Test
    fun `should wire the S3 adapter behind the application port`() {
        assertInstanceOf(S3DocumentStorage::class.java, storage)
    }
}
