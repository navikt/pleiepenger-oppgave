package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.github.tomakehurst.wiremock.matching.StringValuePattern

private const val sparkelPath = "/sparkel-mock"
private const val oppgavePath = "/oppgave-mock"

internal fun stubOppgaveOk(oppgaveId: String,
                  sokerAktoerId: String) {
    WireMock.stubFor(
        WireMock.post(WireMock.urlPathMatching(".*$oppgavePath.*"))
            .withRequestBody(
                ContainsPattern("""
                    "aktoerId":"$sokerAktoerId"
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

internal fun stubSparkelGetBehandlendeEnhetKunHovedSoeker(
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

internal fun stubSparkelGetBehandlendeEnhetHovedSoekerOgMedSoeker(
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

internal fun WireMockServer.stubSparkelReady() = stubReady("$sparkelPath/isready")
internal fun WireMockServer.stubOppgaveReady() = stubReady("$oppgavePath/internal/ready")

private fun WireMockServer.stubReady(
    path: String
) : WireMockServer {
    WireMock.stubFor(
        WireMock.get(WireMock.urlPathMatching(".*$path"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
            )
    )
    return this
}

internal fun WireMockServer.getSparkelBaseUrl() = baseUrl() + sparkelPath

internal fun WireMockServer.getOppgaveBaseUrl() = baseUrl() + oppgavePath