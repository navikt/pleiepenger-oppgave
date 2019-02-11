package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Extension
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.WiremockWrapper")
private const val jwkSetPath = "/auth-mock/jwk-set"
private const val tokenPath = "/auth-mock/token"
private const val joarkInngaaendeForsendelsePath = "/joark-mock/rest/mottaInngaaendeForsendelse"

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

        stubJoark()
        stubGetSystembrukerToken()

        logger.info("Mock available on '{}'", wireMockServer.baseUrl())
        return wireMockServer
    }

    private fun stubJoark() {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching(".*$joarkInngaaendeForsendelsePath"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "journalpostId": "1234",
                                "journalTilstand": "ENDELIG_JOURNALFOERT"
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
}

fun WireMockServer.getJwksUrl() : String {
    return baseUrl() + jwkSetPath
}

fun WireMockServer.getTokenUrl() : String {
    return baseUrl() + tokenPath
}

fun WireMockServer.getJoarkInngaaendeForsendelseUrl() : String {
    return baseUrl() + joarkInngaaendeForsendelsePath
}
