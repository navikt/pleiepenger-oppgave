package no.nav.helse.oppgave.gateway

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.*
import no.nav.helse.CorrelationId
import no.nav.helse.HttpRequest
import no.nav.helse.dusseldorf.ktor.client.MonitoredHttpClient
import no.nav.helse.dusseldorf.ktor.client.SystemCredentialsProvider
import no.nav.helse.dusseldorf.ktor.client.setProxyRoutePlanner
import no.nav.helse.dusseldorf.ktor.client.sl4jLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.OppgaveGateway")

/*
    https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
 */
class OppgaveGateway(
    oppgaveBaseUrl: URL,
    private val systemCredentialsProvider: SystemCredentialsProvider
) {

    private val monitoredHttpClient = MonitoredHttpClient(
        source = "pleiepenger-oppgave",
        destination = "oppgave",
        httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer { configureObjectMapper(this) }
            }
            engine {
                customizeClient { setProxyRoutePlanner() }
            }
            install (Logging) {
                sl4jLogger("pleiepenger-oppgave")
            }
        }
    )

    private val opprettOppgaveUrl : URL = HttpRequest.buildURL(oppgaveBaseUrl, pathParts = listOf("api", "v1", "oppgaver"))

    suspend fun opprettOppgave(
        request : OpprettOppgaveRequest,
        correlationId : CorrelationId
    ) : OpprettOppgaveResponse {
        val httpRequest = HttpRequestBuilder()
        httpRequest.header(HttpHeaders.Authorization, systemCredentialsProvider.getAuthorizationHeader())
        httpRequest.header(HttpHeaders.XCorrelationId, correlationId.value)
        httpRequest.method = HttpMethod.Post
        httpRequest.contentType(ContentType.Application.Json)
        httpRequest.body = request
        httpRequest.url(opprettOppgaveUrl)

        return monitoredHttpClient.requestAndReceive(
            httpRequestBuilder = httpRequest,
            expectedHttpResponseCodes = setOf(HttpStatusCode.Created)
        )
    }

    private fun configureObjectMapper(objectMapper: ObjectMapper) : ObjectMapper {
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.registerModule(JavaTimeModule())
        return objectMapper
    }
}