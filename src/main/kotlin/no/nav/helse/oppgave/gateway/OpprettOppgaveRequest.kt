package no.nav.helse.oppgave.gateway

import java.time.LocalDate

data class OpprettOppgaveRequest(
    val tildeltEnhetsnr : String,
    val aktoerId: String,
    val prioritet: String,

    val journalpostId: String,
    val journalpostkilde : String,

    val behandlesAvApplikasjon: String,
    val saksreferanse: String,

    val tema : String,
    val behandlingstema : String,
    val temagruppe: String,
    val oppgavetype: String,
    val behandlingstype: String,

    val mappeId : String,

    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate,

    val beskrivelse : String? = null // Team oppgavehåndtering vil egentlig ikke at vi skal bruke dette feltet...
)

enum class Prioritet {
    LAV,
    NORM,
    HOY
}