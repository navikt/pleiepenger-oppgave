package no.nav.helse

import au.com.dius.pact.consumer.Pact
import au.com.dius.pact.consumer.PactProviderRuleMk2
import au.com.dius.pact.consumer.PactVerification
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.model.RequestResponsePact
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import no.nav.helse.behandlendeenhet.BehandlendeEnhetService
import no.nav.helse.behandlendeenhet.Enhet
import no.nav.helse.behandlendeenhet.SparkelGateway
import no.nav.helse.oppgave.gateway.OppgaveGateway
import no.nav.helse.oppgave.v1.*
import no.nav.helse.systembruker.Response
import no.nav.helse.systembruker.SystembrukerGateway
import no.nav.helse.systembruker.SystembrukerService
import org.junit.Rule
import org.junit.Test
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals

private const val provider = "oppgave"
private const val consumer = "pleiepenger-oppgave"
private const val jwt = "dummy"
private const val aktoerId = "1831212532188"
private const val barnaAktoerId = "1831212532190"
private const val journalpostId = "137662692"
private const val enhetsNummer = "4132"
private const val oppgaveId = "58487564"



private val aktivDato = Date(1553249225316)
private val fristFerdigstillelse = Date(aktivDato.time + (Duration.ofDays(3).toMillis()))


private const val correlationId = "50e93afa-a22d-448a-8fbe-a4a4dae67eb0"

private val logger: Logger = LoggerFactory.getLogger("nav.OppgavePactTests")

class OppgavePactTests {
    @Rule
    @JvmField
    val mockProvider = PactProviderRuleMk2(provider, this)

    init {
        System.setProperty("pact.rootDir", "${System.getProperty("user.dir")}/pacts")
    }

    @Pact(consumer = consumer)
    @SuppressWarnings("unused")
    fun oppretteOppgavePact(builder: PactDslWithProvider): RequestResponsePact {

        val requestBody = PactDslJsonBody()
            .stringValue("tema", "OMS")
            .stringValue("prioritet", "NORM")
            .stringValue("behandlesAvApplikasjon", "IT00")
            .stringValue("journalpostkilde", "AS36")
            .stringValue("behandlingstema", "ab0320")
            .stringValue("temagruppe", "FMLI")
            .stringValue("oppgavetype", "JFR")
                
            .stringValue("mappeId", null)
            .stringValue("behandlingstype", null)
            .stringValue("beskrivelse", null)
            .stringValue("saksreferanse", null)

            .stringMatcher("aktoerId", "\\d+", aktoerId)
            .stringMatcher("journalpostId", "\\d+", journalpostId)
            .stringMatcher("tildeltEnhetsnr", "\\d+", enhetsNummer)

            .date("aktivDato", "yyyy-MM-dd" , aktivDato)
            .date("fristFerdigstillelse", "yyyy-MM-dd", fristFerdigstillelse)


        val headers = mapOf(
            Pair(HttpHeaders.ContentType, "application/json"),
            Pair(HttpHeaders.Accept, "application/json"),
            Pair(HttpHeaders.Authorization, "Bearer $jwt")
        )

        return builder
            .given("Klar for aa opprette nye oppgaver")
            .uponReceiving("Request for aa opprette ny oppgave")
            .path("/api/v1/oppgaver")
            .method("POST")
            .headers(headers)
            .matchHeader(
                HttpHeaders.XCorrelationId,
                "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b",
                correlationId
            )
            .body(requestBody)
            .willRespondWith()
            .headers(mapOf(Pair(HttpHeaders.ContentType, "application/json")))
            .status(201)
            .body(
                PactDslJsonBody().stringMatcher("id", "\\d+", oppgaveId)
            )
            .toPact()
    }





    @Test
    @PactVerification(provider)
    fun opprettelseAvOppgaveFungerer() {
        val oppgaveService = oppgaveService()
        runBlocking {
            val actualOppgaveId = oppgaveService.opprettOppgave(
               melding = MeldingV1(
                   soker = Soker(aktoerId = aktoerId),
                   barn = Barn(aktoerId = barnaAktoerId),
                   journalPostId = journalpostId
               ),
                metaData = MetadataV1(
                    version = 1,
                    correlationId = correlationId,
                    requestId = UUID.randomUUID().toString()
                )
            )
            assertEquals(oppgaveId, actualOppgaveId.id)
        }
    }

    private fun oppgaveService(): OpprettOppgaveV1Service {
        return OpprettOppgaveV1Service(
            oppgaveGateway = oppgaveGateway(),
            behandlendeEnhetService = BehandlendeEnhetService(
                sparkelGateway = mockSparkelGateway()
            )
        )
    }

    private fun oppgaveGateway(): OppgaveGateway {
        val httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer { ObjectMapper.sparkelOgOppgave(this) }
            }
        }

        return OppgaveGateway(
            oppgaveBaseUrl = URL(mockProvider.url),
            systembrukerService = SystembrukerService(
                systembrukerGateway = mockSystembrukerGateway()
            ),
            httpClient = httpClient
        )
    }

    private fun mockSystembrukerGateway(): SystembrukerGateway {
        val mock = mock<SystembrukerGateway>()
        runBlocking {
            whenever(mock.getToken()).thenReturn(
                Response(
                    accessToken = jwt,
                    expiresIn = 5000
                )
            )
        }
        return mock
    }

    private fun mockSparkelGateway() : SparkelGateway {
        val mock = mock<SparkelGateway>()
        runBlocking {
            whenever(mock.hentBehandlendeEnhet(any(), any(), any(), any())).thenReturn(
                Enhet(
                    id = enhetsNummer,
                    navn = "Follo"
                )
            )
        }
        return mock
    }
}