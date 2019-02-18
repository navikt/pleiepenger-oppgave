package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Extension
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import no.nav.security.oidc.test.support.JwkGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.WiremockWrapper")
private const val jwkSetPath = "/auth-mock/jwk-set"
private const val tokenPath = "/auth-mock/token"
private const val getAccessTokenPath = "/auth-mock/get-test-access-token"
private const val sparkelPath = "/sparkel-mock"
private const val oppgavePath = "/oppgave-mock"
private const val subject = "srvpleiepenger-sak"


object WiremockWrapper {

    fun bootstrap(
        port: Int? = null,
        extensions : Array<Extension> = arrayOf()) : WireMockServer {

        val wireMockConfiguration = WireMockConfiguration.options()

        extensions.forEach {
            wireMockConfiguration.extensions(it)
        }

        if (port == null) {
            wireMockConfiguration.dynamicPort()
        } else {
            wireMockConfiguration.port(port)
        }

        val wireMockServer = WireMockServer(wireMockConfiguration)

        wireMockServer.start()
        WireMock.configureFor(wireMockServer.port())

        stubGetSystembrukerToken()
        stubJwkSet()

        provideGetAccessTokenEndPoint(wireMockServer.baseUrl())

        logger.info("Mock available on '{}'", wireMockServer.baseUrl())
        return wireMockServer
    }

    fun stubOppgaveOk(oppgaveId: String,
                      sokerAktoerId: String) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlPathMatching(".*$oppgavePath.*"))
                .withRequestBody(
                    ContainsPattern("""
                    "aktoerId" : "$sokerAktoerId"
                """.trimIndent())
                )
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                            "id" : "$oppgaveId"
                        }
                        """.trimIndent())
                )
        )
    }

    fun stubSparkelGetBehandlendeEnhetKunHovedSoeker(
        sokerAktoerId: String,
        enhetId: String,
        enhetNavn: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$sparkelPath/api/arbeidsfordeling/behandlende-enhet/$sokerAktoerId"))
                .withQueryParam("medAktoerId", StringValuePattern.ABSENT)
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                            "id" : "$enhetId",
                            "navn": "$enhetNavn"
                        }
                        """.trimIndent())
                )
        )
    }

    fun stubSparkelGetBehandlendeEnhetHovedSoekerOgMedSoeker(
        sokerAktoerId: String,
        medSoekerId : String,
        enhetId: String,
        enhetNavn: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$sparkelPath/api/arbeidsfordeling/behandlende-enhet/$sokerAktoerId"))
                .withQueryParam("medAktoerId", ContainsPattern(medSoekerId))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                        {
                            "id" : "$enhetId",
                            "navn": "$enhetNavn"
                        }
                        """.trimIndent())
                )
        )
    }

    private fun stubGetSystembrukerToken() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$tokenPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"i-am-an-access-token\", \"expires_in\": 5000}")
                )
        )
    }

    private fun stubJwkSet() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$jwkSetPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody(WiremockWrapper::class.java.getResource(JwkGenerator.DEFAULT_JWKSET_FILE).readText())
                )
        )
    }

    private fun provideGetAccessTokenEndPoint(issuer: String) {
        val jwt = Authorization.getAccessToken(issuer, subject)
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$getAccessTokenPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"$jwt\", \"expires_in\": 5000}")
                )
        )
    }
}

fun WireMockServer.getJwksUrl() : String {
    return baseUrl() + jwkSetPath
}

fun WireMockServer.getTokenUrl() : String {
    return baseUrl() + tokenPath
}

fun WireMockServer.getSparkelBaseUrl() : String {
    return baseUrl() + sparkelPath
}

fun WireMockServer.getOppgaveBaseUrl() : String {
    return baseUrl() + oppgavePath
}

fun WireMockServer.getSubject() : String {
    return subject
}