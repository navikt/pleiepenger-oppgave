package no.nav.helse.behandlendeenhet

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.prometheus.client.Histogram
import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.HttpRequest
import no.nav.helse.Tema
import no.nav.helse.systembruker.SystembrukerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private const val SPARKEL_CORRELATION_ID_HEADER = "Nav-Call-Id"

private val logger: Logger = LoggerFactory.getLogger("nav.SparkelGateway")

private val hentBehandlendeEnhet = Histogram.build(
    "histogram_hent_behandlende_enhet",
    "Tidsbruk henting av behandlende enhet fra Sparkel"
).register()

class SparkelGateway(
    private val httpClient : HttpClient,
    baseUrl : URL,
    private val systembrukerService: SystembrukerService) {

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
        httpRequest.header(HttpHeaders.Authorization, systembrukerService.getAuthorizationHeader())
        httpRequest.header(SPARKEL_CORRELATION_ID_HEADER, correlationId.value)
        httpRequest.method = HttpMethod.Get
        httpRequest.accept(ContentType.Application.Json)
        httpRequest.url(url)

        return HttpRequest.monitored(
            httpClient = httpClient,
            httpRequest = httpRequest,
            histogram = hentBehandlendeEnhet
        )
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
}