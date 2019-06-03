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
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private const val SPARKEL_CORRELATION_ID_HEADER = "Nav-Call-Id"

private val logger: Logger = LoggerFactory.getLogger("nav.SparkelGateway")

class SparkelGateway(
    baseUrl : URL,
    private val accessTokenClient: CachedAccessTokenClient) {

    private val hentBehandlendeEnhetBaseUrl: URL = Url.buildURL(
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

        val (_, _, result) =
            Operation.monitored(
                app = "pleiepenger-oppgave",
                operation = "hente-behandlende-enhet",
                resultResolver = { 200 == it.second.statusCode }
            ) {
                url.httpGet().header(
                    Headers.ACCEPT to "application/json",
                    SPARKEL_CORRELATION_ID_HEADER to correlationId.value,
                    Headers.AUTHORIZATION to authorizationHeader
                ).awaitStringResponseResult()
            }

        return result.fold(
            { success -> objectMapper.readValue(success) },
            { error ->
                logger.error(error.toString())
                throw IllegalStateException("Feil ved henting av behandlende enhet.")
            }
        )
    }

    private fun queryParameters(
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