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
    val behandlingstype: String,

    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate,

    val mappeId : String? = null, // MappId varierer fra enhet til enhet, Så om vi ønsker en mappe ved opprettelse må også integrasjon mot /v1/mappe gjøres for å forsikre oss om at mappen finnes..
    val saksreferanse: String? = null, // Settes ikke så lenge sak opprettes i gosys
    val beskrivelse : String? = null // Team oppgavehåndtering vil egentlig ikke at vi skal bruke dette feltet...
)

enum class Prioritet {
    LAV,
    NORM,
    HOY
}