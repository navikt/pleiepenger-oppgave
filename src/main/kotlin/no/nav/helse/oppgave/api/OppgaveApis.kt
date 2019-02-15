package no.nav.helse.oppgave.api

import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.Tema
import no.nav.helse.behandlendeenhet.BehandlendeEnhetService
import no.nav.helse.oppgave.v1.MeldingV1
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.oppgaveApis")

fun Route.oppgaveApis(
    behandlendeEnhetService: BehandlendeEnhetService
) {

    post("v1/oppgave") {
        val melding = call.receive<MeldingV1>()
        call.respond(HttpStatusCode.OK, behandlendeEnhetService.hentBehandlendeEnhet(
            sokerAktoerId = AktoerId(melding.soker.aktoerId),
            tema = Tema(melding.tema),
            correlationId = CorrelationId(call.request.getCorrelationId())
        ))
    }
}

private fun ApplicationRequest.getCorrelationId(): String {
    return header(HttpHeaders.XCorrelationId) ?: throw ManglerCorrelationId()
}

private fun ApplicationRequest.getRequestId(): String? {
    return header(HttpHeaders.XRequestId)
}