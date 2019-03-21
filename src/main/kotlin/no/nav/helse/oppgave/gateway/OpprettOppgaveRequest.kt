package no.nav.helse.oppgave.gateway

import java.time.LocalDate

data class OpprettOppgaveRequest(
    val tildeltEnhetsnr : String,
    val aktoerId: String,
    val prioritet: String,

    val journalpostId: String,
    val journalpostkilde : String,

    val behandlesAvApplikasjon: String,

    val tema : String,
    val behandlingstema : String,
    val temagruppe: String,
    val oppgavetype: String,

    val mappeId : String,

    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate,

    val saksreferanse: String? = null, // Settes ikke så lenge sak opprettes i gosys
    val beskrivelse : String? = null, // Team oppgavehåndtering vil egentlig ikke at vi skal bruke dette feltet...
    val behandlingstype: String? = null // Om denne settes valideres det om det er gyldig kombinasjon med tema og behandlingstema. Uklart hvordan vi "får" en slik kombinasjon og om den trengs..
)

enum class Prioritet {
    LAV,
    NORM,
    HOY
}