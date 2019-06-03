package no.nav.helse

import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.jackson.jackson
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.behandlendeenhet.BehandlendeEnhetService
import no.nav.helse.behandlendeenhet.SparkelGateway
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.TryCatchHealthCheck
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.dusseldorf.ktor.metrics.init
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.helse.oppgave.api.oppgaveApis
import no.nav.helse.oppgave.gateway.OppgaveGateway
import no.nav.helse.oppgave.v1.OpprettOppgaveV1Service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerOppgave")

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengerOppgave() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)
    val issuers = configuration.issuers()

    install(Authentication) {
        multipleJwtIssuers(issuers)
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        AuthStatusPages()
    }

    val naisStsClient = configuration.naisStsClient()
    val naisStsAccessTokenClient = NaisStsAccessTokenClient(
        clientId = naisStsClient.clientId(),
        clientSecret = naisStsClient.clientSecret,
        tokenEndpoint = naisStsClient.tokenEndpoint()
    )
    val cachedNaisStsAccessTokenClient = CachedAccessTokenClient(naisStsAccessTokenClient)

    install(CallIdRequired)

    install(Routing) {
        authenticate (*issuers.allIssuers()) {
            requiresCallId {
                oppgaveApis(
                    opprettOppgaveV1Service = OpprettOppgaveV1Service(
                        behandlendeEnhetService = BehandlendeEnhetService(
                            sparkelGateway = SparkelGateway(
                                baseUrl = configuration.getSparkelBaseUrl(),
                                accessTokenClient = cachedNaisStsAccessTokenClient
                            )
                        ),
                        oppgaveGateway = OppgaveGateway(
                            oppgaveBaseUrl = configuration.getOppgaveBaseUrl(),
                            accessTokenClient = cachedNaisStsAccessTokenClient
                        )
                    )

                )
            }

        }
        DefaultProbeRoutes()
        MetricsRoute()
        HealthRoute(
            healthChecks = setOf(
                TryCatchHealthCheck(
                    name = "NaisStsAccessTokenHealthCheck"
                ) {
                    naisStsAccessTokenClient.getAccessToken(setOf("openid"))
                },
                HttpRequestHealthCheck(
                    urlExpectedHttpStatusCodeMap = issuers.healthCheckMap(mutableMapOf(
                        Url.buildURL(baseUrl = configuration.getOppgaveBaseUrl(), pathParts = listOf("internal", "ready")) to HttpStatusCode.OK,
                        Url.buildURL(baseUrl = configuration.getSparkelBaseUrl(), pathParts = listOf("isready")) to HttpStatusCode.OK
                    ))
                )
            )
        )
    }

    install(MicrometerMetrics) {
        init(appId)
    }

    install(CallId) {
        fromXCorrelationIdHeader()
    }

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }
}

private fun Map<Issuer, Set<ClaimRule>>.healthCheckMap(
    initial : MutableMap<URL, HttpStatusCode>
) : Map<URL, HttpStatusCode> {
    forEach { issuer, _ ->
        initial[issuer.jwksUri()] = HttpStatusCode.OK
    }
    return initial.toMap()
}
