package no.nav.helse

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.Logging
import io.ktor.features.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.jackson.jackson
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.behandlendeenhet.BehandlendeEnhetService
import no.nav.helse.behandlendeenhet.SparkelGateway
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.CallMonitoring
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.oppgave.api.oppgaveApis
import no.nav.helse.oppgave.gateway.OppgaveGateway
import no.nav.helse.oppgave.v1.OpprettOppgaveV1Service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerOppgave")

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengerOppgave() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)
    val authorizedSystems = configuration.getAuthorizedSystemsForRestApi()

    val jwkProvider = JwkProviderBuilder(configuration.getJwksUrl())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt {
            verifier(jwkProvider, configuration.getIssuer())
            realm = appId
            validate { credentials ->
                logger.info("authorization attempt for ${credentials.payload.subject}")
                if (credentials.payload.subject in authorizedSystems) {
                    logger.info("authorization ok")
                    return@validate JWTPrincipal(credentials.payload)
                }
                logger.warn("authorization failed")
                return@validate null
            }
        }
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
    }

    val systemCredentialsProvider = Oauth2ClientCredentialsProvider(
        monitoredHttpClient = MonitoredHttpClient(
            source = appId,
            destination = "nais-sts",
            httpClient = HttpClient(Apache) {
                install(JsonFeature) {
                    serializer = JacksonSerializer {
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
                install (Logging) {
                    sl4jLogger("nais-sts")
                }
                engine {
                    customizeClient { setProxyRoutePlanner() }
                }
            }
        ),
        tokenUrl = configuration.getTokenUrl(),
        clientId = configuration.getServiceAccountClientId(),
        clientSecret = configuration.getServiceAccountClientSecret(),
        scopes = configuration.getServiceAccountScopes()
    )

    install(CallIdRequired)

    install(Routing) {
        authenticate {
            requiresCallId {
                oppgaveApis(
                    opprettOppgaveV1Service = OpprettOppgaveV1Service(
                        behandlendeEnhetService = BehandlendeEnhetService(
                            sparkelGateway = SparkelGateway(
                                baseUrl = configuration.getSparkelBaseUrl(),
                                systemCredentialsProvider = systemCredentialsProvider
                            )
                        ),
                        oppgaveGateway = OppgaveGateway(
                            oppgaveBaseUrl = configuration.getOppgaveBaseUrl(),
                            systemCredentialsProvider = systemCredentialsProvider
                        )
                    )

                )
            }

        }
        DefaultProbeRoutes()
        MetricsRoute()
        HealthRoute(
            healthChecks = setOf(
                SystemCredentialsProviderHealthCheck(systemCredentialsProvider),
                HttpRequestHealthCheck(
                    app = appId,
                    urlExpectedHttpStatusCodeMap = mapOf(
                        configuration.getJwksUrl() to HttpStatusCode.OK,
                        Url.buildURL(baseUrl = configuration.getOppgaveBaseUrl(), pathParts = listOf("internal", "ready")) to HttpStatusCode.OK,
                        Url.buildURL(baseUrl = configuration.getSparkelBaseUrl(), pathParts = listOf("isready")) to HttpStatusCode.OK
                    )
                )
            )
        )
    }

    install(CallMonitoring) {
        app = appId
    }

    install(CallId) {
        fromXCorrelationIdHeader()
    }

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }
}