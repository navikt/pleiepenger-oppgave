package no.nav.helse.oppgave.gateway

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.*
import io.prometheus.client.Histogram
import no.nav.helse.CorrelationId
import no.nav.helse.HttpRequest
import no.nav.helse.systembruker.SystembrukerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.OppgaveGateway")

private val opprettOppgave = Histogram.build(
    "histogram_opprett_oppgave",
    "Tidsbruk for opprettelse av Oppgave mot oppgave"
).register()


/*
    https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
 */
class OppgaveGateway(
    private val httpClient: HttpClient,
    oppgaveBaseUrl: URL,
    private val systembrukerService: SystembrukerService
) {

    private val opprettOppgaveUrl : URL = HttpRequest.buildURL(oppgaveBaseUrl, pathParts = listOf("api", "v1", "oppgaver"))

    suspend fun opprettOppgave(
        request : OpprettOppgaveRequest,
        correlationId : CorrelationId
    ) : OpprettOppgaveResponse {
        val httpRequest = HttpRequestBuilder()
        httpRequest.header(HttpHeaders.Authorization, systembrukerService.getAuthorizationHeader())
        httpRequest.header(HttpHeaders.XCorrelationId, correlationId.value)
        httpRequest.method = HttpMethod.Post
        httpRequest.contentType(ContentType.Application.Json)
        httpRequest.body = request
        httpRequest.url(opprettOppgaveUrl)

        return HttpRequest.monitored(
            httpClient = httpClient,
            httpRequest = httpRequest,
            histogram = opprettOppgave,
            expectedStatusCodes = listOf(HttpStatusCode.Created)
        )
    }
}