package no.nav.helse.oppgave.gateway

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.*
import no.nav.helse.CorrelationId
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI

/*
    https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
 */
class OppgaveGateway(
    oppgaveBaseUrl: URI,
    private val accessTokenClient: CachedAccessTokenClient
) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.OppgaveGateway")
    }

    private val opprettOppgaveUrl = Url.buildURL(oppgaveBaseUrl, pathParts = listOf("api", "v1", "oppgaver")).toString()
    private val objectMapper = configuredObjectMapper()

    suspend fun opprettOppgave(
        opprettOppgaveRequest : OpprettOppgaveRequest,
        correlationId : CorrelationId
    ) : OpprettOppgaveResponse {

        val authorizationHeader = accessTokenClient.getAccessToken(setOf("openid")).asAuthoriationHeader()
        val body = configuredObjectMapper().writeValueAsBytes(opprettOppgaveRequest)
        val contentStream = { ByteArrayInputStream(body) }

        val httpRequest = opprettOppgaveUrl.httpPost()
            .body(contentStream)
            .header(
                Headers.CONTENT_TYPE to "application/json",
                Headers.ACCEPT to "application/json",
                HttpHeaders.XCorrelationId to correlationId.value,
                Headers.AUTHORIZATION to authorizationHeader
            )

        val (request,_, result) = Operation.monitored(
            app = "pleiepenger-oppgave",
            operation = "opprettet-oppgave",
            resultResolver = { 201 == it.second.statusCode }
        ) {
            httpRequest.awaitStringResponseResult()
        }

        return result.fold(
            { success -> objectMapper.readValue(success) },
            { error ->
                logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                logger.error(error.toString())
                throw IllegalStateException("Feil ved Opprettelse av oppgave")
            }
        )
    }

    private fun configuredObjectMapper() : ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.registerModule(JavaTimeModule())
        return objectMapper
    }
}