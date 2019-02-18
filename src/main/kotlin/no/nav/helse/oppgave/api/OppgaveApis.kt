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
import no.nav.helse.oppgave.v1.MeldingV1
import no.nav.helse.oppgave.v1.MetadataV1
import no.nav.helse.oppgave.v1.OpprettOppgaveV1Service
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.oppgaveApis")

fun Route.oppgaveApis(
    opprettOppgaveV1Service: OpprettOppgaveV1Service
) {

    post("v1/oppgave") {
        val melding = call.receive<MeldingV1>()
        val metaData = MetadataV1(
            correlationId = call.request.getCorrelationId(),
            requestId = call.request.getRequestId(),
            version = 1
        )

        val oppgaveId = opprettOppgaveV1Service.opprettOppgave(
            melding = melding,
            metaData = metaData
        )

        call.respond(HttpStatusCode.Created, OppgaveResponse(oppgaveId.id))
    }
}

private fun ApplicationRequest.getCorrelationId(): String {
    return header(HttpHeaders.XCorrelationId) ?: throw ManglerCorrelationId()
}

private fun ApplicationRequest.getRequestId(): String? {
    return header(HttpHeaders.XRequestId)
}

data class OppgaveResponse(val oppgaveId: String)