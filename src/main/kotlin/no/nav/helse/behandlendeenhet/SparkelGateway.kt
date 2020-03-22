package no.nav.helse.behandlendeenhet

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.Url
import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.Tema
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration


class SparkelGateway(
    baseUrl : URI,
    private val accessTokenClient: CachedAccessTokenClient) {

    private companion object {
        private const val SPARKEL_CORRELATION_ID_HEADER = "Nav-Call-Id"
        private const val HENTE_BEHANDLENDE_ENHET_OPERATION = "hente-behandlende-enhet"
        private val logger: Logger = LoggerFactory.getLogger("nav.SparkelGateway")
    }

    private val hentBehandlendeEnhetBaseUrl = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("api","arbeidsfordeling", "behandlende-enhet")
    )

    private val objectMapper = configuredObjectMapper()

    suspend fun hentBehandlendeEnhet(
        hovedAktoer : AktoerId,
        medAktoerer : List<AktoerId>,
        tema: Tema,
        correlationId: CorrelationId
    ) : Enhet {

        val authorizationHeader = accessTokenClient.getAccessToken(setOf("openid")).asAuthoriationHeader()

        val url = Url.buildURL(
            baseUrl = hentBehandlendeEnhetBaseUrl,
            pathParts = listOf(hovedAktoer.value),
            queryParameters = queryParameters(
                tema = tema,
                medAktoerer = medAktoerer
            )
        ).toString()

        val httpRequest = url.httpGet().header(
            Headers.ACCEPT to "application/json",
            SPARKEL_CORRELATION_ID_HEADER to correlationId.value,
            Headers.AUTHORIZATION to authorizationHeader
        )

        return Retry.retry(
            operation = HENTE_BEHANDLENDE_ENHET_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0
        ) {
            val (request, _, result) = Operation.monitored(
                app = "pleiepenger-oppgave",
                operation = HENTE_BEHANDLENDE_ENHET_OPERATION,
                resultResolver = { 200 == it.second.statusCode }
            ) { httpRequest.awaitStringResponseResult() }
            result.fold(
                { success -> objectMapper.readValue<Enhet>(success) },
                { error ->
                    logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                    logger.error(error.toString())
                    throw IllegalStateException("Feil ved henting av behandlende enhet.")
                }
            )
        }
    }

    private fun queryParameters (
        tema: Tema,
        medAktoerer: List<AktoerId>
    ) : Map<String, List<String>> {
        val queryParameters = mutableMapOf<String, List<String>>()
        queryParameters.put("tema", listOf(tema.value))

        val akoerIdStringList = mutableListOf<String>()
        medAktoerer.forEach {
            akoerIdStringList.add(it.value)
        }
        queryParameters.put("medAktoerId", akoerIdStringList.toList())
        return queryParameters.toMap()
    }

    private fun configuredObjectMapper() : ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return objectMapper
    }
}