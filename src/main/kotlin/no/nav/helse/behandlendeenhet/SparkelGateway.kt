package no.nav.helse.behandlendeenhet

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.HttpRequest
import no.nav.helse.Tema
import no.nav.helse.dusseldorf.ktor.client.MonitoredHttpClient
import no.nav.helse.dusseldorf.ktor.client.SystemCredentialsProvider
import no.nav.helse.dusseldorf.ktor.client.setProxyRoutePlanner
import no.nav.helse.dusseldorf.ktor.client.sl4jLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private const val SPARKEL_CORRELATION_ID_HEADER = "Nav-Call-Id"

private val logger: Logger = LoggerFactory.getLogger("nav.SparkelGateway")

class SparkelGateway(
    baseUrl : URL,
    private val systemCredentialsProvider: SystemCredentialsProvider) {

    private val monitoredHttpClient = MonitoredHttpClient(
        source = "pleiepenger-oppgave",
        destination = "sparkel",
        overridePaths = mapOf(
            Pair(Regex("/api/arbeidsfordeling/behandlende-enhet/.*"), "/api/arbeidsfordeling/behandlende-enhet")
        ),
        httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer { configureObjectMapper(this) }
            }
            engine {
                customizeClient { setProxyRoutePlanner() }
            }
            install (Logging) {
                sl4jLogger("pleiepenger-dokument")
            }
        }
    )

    private val hentBehandlendeEnhetBaseUrl: URL = HttpRequest.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("api","arbeidsfordeling", "behandlende-enhet")
    )

    suspend fun hentBehandlendeEnhet(
        hovedAktoer : AktoerId,
        medAktoerer : List<AktoerId>,
        tema: Tema,
        correlationId: CorrelationId
    ) : Enhet {

        val url = HttpRequest.buildURL(
            baseUrl = hentBehandlendeEnhetBaseUrl,
            pathParts = listOf(hovedAktoer.value),
            queryParameters = queryParameters(
                tema = tema,
                medAktoerer = medAktoerer
            )
        )

        val httpRequest = HttpRequestBuilder()
        httpRequest.header(HttpHeaders.Authorization, systemCredentialsProvider.getAuthorizationHeader())
        httpRequest.header(SPARKEL_CORRELATION_ID_HEADER, correlationId.value)
        httpRequest.method = HttpMethod.Get
        httpRequest.accept(ContentType.Application.Json)
        httpRequest.url(url)
        return monitoredHttpClient.requestAndReceive(httpRequest)
    }

    private fun queryParameters(
        tema: Tema,
        medAktoerer: List<AktoerId>
    ) : Map<String, List<String>> {
        val queryParameters = mutableMapOf<String, List<String>>()
        queryParameters.put("tema", listOf(tema.value))

        val akoerIdStringList = mutableListOf<String>()
        medAktoerer.forEach { it ->
            akoerIdStringList.add(it.value)
        }
        queryParameters.put("medAktoerId", akoerIdStringList.toList())
        return queryParameters.toMap()
    }

    private fun configureObjectMapper(objectMapper: ObjectMapper) : ObjectMapper {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return objectMapper
    }
}