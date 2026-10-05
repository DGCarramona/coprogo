package tech.justdev.interfaces.configuration

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.annotation.PostConstruct
import java.time.Duration

@ConfigurationProperties(SupportingDocumentDownloadConfiguration.PREFIX)
class SupportingDocumentDownloadConfiguration {
    var validFor: Duration = Duration.ofMinutes(5)

    @PostConstruct
    fun validate() {
        require(validFor > Duration.ZERO) { "$PREFIX.valid-for must be strictly positive" }
    }

    companion object {
        const val PREFIX = "coprogo.documents.download"
    }
}
