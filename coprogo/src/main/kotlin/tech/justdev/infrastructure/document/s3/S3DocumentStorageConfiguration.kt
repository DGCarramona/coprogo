package tech.justdev.infrastructure.document.s3

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import tech.justdev.application.document.DocumentStorage
import java.net.URI

@ConfigurationProperties(S3DocumentStorageConfiguration.PREFIX)
class S3DocumentStorageConfiguration {
    var bucket: String = ""
    var region: String = ""
    var endpoint: URI? = null
    var pathStyleAccess: Boolean = false

    @PostConstruct
    fun validate() {
        bucket = bucket.trim()
        region = region.trim()
        require(bucket.isNotEmpty()) { "$PREFIX.bucket must not be blank" }
        require(region.isNotEmpty()) { "$PREFIX.region must not be blank" }
        require(endpoint == null || endpoint?.scheme in setOf("http", "https")) {
            "$PREFIX.endpoint must use HTTP or HTTPS"
        }
    }

    companion object {
        const val PREFIX = "coprogo.documents.s3"
    }
}

@Factory
@Requires(property = "coprogo.documents.s3.bucket")
class S3DocumentStorageFactory {
    @Bean(preDestroy = "close")
    @Singleton
    fun client(configuration: S3DocumentStorageConfiguration): S3AsyncClient {
        val builder =
            S3AsyncClient
                .builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(Region.of(configuration.region))
                .serviceConfiguration(serviceConfiguration(configuration))
        configuration.endpoint?.let(builder::endpointOverride)
        return builder.build()
    }

    @Bean(preDestroy = "close")
    @Singleton
    fun presigner(configuration: S3DocumentStorageConfiguration): S3Presigner {
        val builder =
            S3Presigner
                .builder()
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .region(Region.of(configuration.region))
                .serviceConfiguration(serviceConfiguration(configuration))
        configuration.endpoint?.let(builder::endpointOverride)
        return builder.build()
    }

    @Singleton
    fun storage(
        client: S3AsyncClient,
        presigner: S3Presigner,
        configuration: S3DocumentStorageConfiguration,
    ): DocumentStorage = S3DocumentStorage(client, presigner, configuration.bucket)

    private fun serviceConfiguration(configuration: S3DocumentStorageConfiguration): S3Configuration =
        S3Configuration
            .builder()
            .pathStyleAccessEnabled(configuration.pathStyleAccess)
            .build()
}
