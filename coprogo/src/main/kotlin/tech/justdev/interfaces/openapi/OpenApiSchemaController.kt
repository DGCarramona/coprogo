package tech.justdev.interfaces.openapi

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.Hidden

@Controller
class OpenApiSchemaController {

    @Get("/openapi.yml")
    @Hidden
    @Produces("application/yaml")
    @Secured(SecurityRule.IS_ANONYMOUS)
    fun schema(): HttpResponse<ByteArray> {
        val schemaBytes = javaClass.classLoader.getResourceAsStream(OPENAPI_SCHEMA_RESOURCE)
            ?.use { it.readAllBytes() }
            ?: return HttpResponse.notFound()

        return HttpResponse.ok(schemaBytes)
            .contentType(MediaType.of("application/yaml"))
    }

    private companion object {
        const val OPENAPI_SCHEMA_RESOURCE = "META-INF/swagger/openapi.yml"
    }
}
