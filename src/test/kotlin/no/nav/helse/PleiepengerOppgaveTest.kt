package no.nav.helse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.oppgave.v1.Barn
import no.nav.helse.oppgave.v1.MeldingV1
import no.nav.helse.oppgave.v1.Soker
import org.junit.AfterClass
import org.junit.BeforeClass
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.*

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerOppgaveTest")

@KtorExperimentalAPI
class PleiepengerOppgaveTest {

    @KtorExperimentalAPI
    private companion object {

        private val wireMockServer: WireMockServer = WiremockWrapper.bootstrap()
        private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
        private val authorizedAccessToken = Authorization.getAccessToken(wireMockServer.baseUrl(), wireMockServer.getSubject())


        fun getConfig() : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(wireMockServer = wireMockServer))
            val mergedConfig = testConfig.withFallback(fileConfig)

            return HoconApplicationConfig(mergedConfig)
        }


        val engine = TestApplicationEngine(createTestEnvironment {
            config = getConfig()
        })


        @BeforeClass
        @JvmStatic
        fun buildUp() {
            engine.start(wait = true)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `gyldig melding uten aktoerID paa barn foerer til en opprettet oppgave med oppgaveID`() {
        val sokerAktoerId = "789456"
        val oppgaveId = "456123"

        val request = MeldingV1(
            soker = Soker(aktoerId = sokerAktoerId),
            barn = Barn(aktoerId = null),
            journalPostId = "13141516"
        )

        WiremockWrapper.stubSparkelGetBehandlendeEnhetKunHovedSoeker(
            sokerAktoerId = sokerAktoerId,
            enhetId = "1234",
            enhetNavn = "Follo"
        )

        WiremockWrapper.stubOppgaveOk(
            oppgaveId = oppgaveId,
            sokerAktoerId = sokerAktoerId
        )

        requestAndAssert(
            request = request,
            expectedResponse = """
                {
                    "oppgave_id" : "$oppgaveId"
                }
            """.trimIndent(),
            expectedCode = HttpStatusCode.Created
        )
    }

    @Test
    fun `gyldig melding med aktoerID paa barn foerer til en opprettet oppgave med oppgaveID`() {
        val sokerAktoerId = "7894561"
        val barnAktoerId = "997788"
        val oppgaveId = "4561231"

        val request = MeldingV1(
            soker = Soker(aktoerId = sokerAktoerId),
            barn = Barn(aktoerId = barnAktoerId),
            journalPostId = "131415161"
        )

        WiremockWrapper.stubSparkelGetBehandlendeEnhetHovedSoekerOgMedSoeker(
            sokerAktoerId = sokerAktoerId,
            medSoekerId = barnAktoerId,
            enhetId = "5678",
            enhetNavn = "VIKEN"
        )

        WiremockWrapper.stubOppgaveOk(
            oppgaveId = oppgaveId,
            sokerAktoerId = sokerAktoerId
        )

        requestAndAssert(
            request = request,
            expectedResponse = """
                {
                    "oppgave_id" : "$oppgaveId"
                }
            """.trimIndent(),
            expectedCode = HttpStatusCode.Created
        )
    }

    @Test
    fun `test isready, isalive og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                    }
                }
            }
        }
    }

    @Test
    fun `test validering av meldingen foer oppgave blir opprettet`() {
        val request = MeldingV1(
            soker = Soker(aktoerId = "FFFF"),
            barn = Barn(aktoerId = ""),
            journalPostId = ""
        )

        requestAndAssert(
            request = request,
            expectedResponse = """
                {
                    "type": "/problem-details/invalid-request-parameters",
                    "title": "invalid-request-parameters",
                    "status": 400,
                    "detail": "Requesten inneholder ugyldige paramtere.",
                    "instance": "about:blank",
                    "invalid_parameters": [{
                        "type": "entity",
                        "name": "soker.aktoer_id",
                        "reason": "Ugyldig AktørID. Kan kun være siffer.",
                        "invalid_value": "FFFF"
                    }, {
                        "type": "entity",
                        "name": "barn.aktoer_id",
                        "reason": "Ugyldig AktørID. Kan kun være siffer.",
                        "invalid_value": ""
                    }, {
                        "type": "entity",
                        "name": "journal_post_id",
                        "reason": "Ugyldig JournalpostID. Kan kun være siffer.",
                        "invalid_value": ""
                    }]
                }
            """.trimIndent(),
            expectedCode = HttpStatusCode.BadRequest
        )
    }


    @Test
    fun `request uten correlationId skal feile`() {
        val request = MeldingV1(
            soker = Soker(aktoerId = "1234"),
            barn = Barn(aktoerId = "5678"),
            journalPostId = "13141516"
        )

        requestAndAssert(
            request = request,
            leggTilCorrelationId = false,
            expectedCode = HttpStatusCode.BadRequest,
            expectedResponse = """
                {
                    "type": "/problem-details/invalid-request-parameters",
                    "title": "invalid-request-parameters",
                    "detail": "Requesten inneholder ugyldige paramtere.",
                    "status": 400,
                    "instance": "about:blank",
                    "invalid_parameters" : [
                        {
                            "name" : "X-Correlation-ID",
                            "reason" : "Correlation ID må settes.",
                            "type": "header",
                            "invalid_value": null
                        }
                    ]
                }
            """.trimIndent()
        )
    }

    @Test
    fun `mangler authorization header`() {
        val request = MeldingV1(
            soker = Soker(aktoerId = "1234"),
            barn = Barn(aktoerId = "5678"),
            journalPostId = "13141516"
        )

        requestAndAssert(
            request = request,
            leggTilAuthorization = false,
            expectedCode = HttpStatusCode.Unauthorized,
            expectedResponse = null
        )
    }

    @Test
    fun `request fra ikke tillatt system`() {
        val request = MeldingV1(
            soker = Soker(aktoerId = "1234"),
            barn = Barn(aktoerId = "5678"),
            journalPostId = "13141516"
        )

        requestAndAssert(
            request = request,
            expectedCode = HttpStatusCode.Unauthorized,
            expectedResponse = null,
            accessToken = Authorization.getAccessToken(wireMockServer.baseUrl(), "srvnotauthorized")
        )
    }

    private fun requestAndAssert(request : MeldingV1,
                                 expectedResponse : String?,
                                 expectedCode : HttpStatusCode,
                                 leggTilCorrelationId : Boolean = true,
                                 leggTilAuthorization : Boolean = true,
                                 accessToken : String = authorizedAccessToken) {
        with(engine) {
            handleRequest(HttpMethod.Post, "/v1/oppgave") {
                if (leggTilAuthorization) {
                    addHeader(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                if (leggTilCorrelationId) {
                    addHeader(HttpHeaders.XCorrelationId, "123156")
                }
                addHeader(HttpHeaders.ContentType, "application/json")
                val requestEntity = objectMapper.writeValueAsString(request)
                logger.info("Request Entity = $requestEntity")
                setBody(objectMapper.writeValueAsString(request))
            }.apply {
                logger.info("Response Entity = ${response.content}")
                logger.info("Expected Entity = $expectedResponse")
                assertEquals(expectedCode, response.status())
                if (expectedResponse != null) {
                    JSONAssert.assertEquals(expectedResponse, response.content!!, false)
                } else {
                    assertEquals(expectedResponse, response.content)
                }

            }
        }
    }
}