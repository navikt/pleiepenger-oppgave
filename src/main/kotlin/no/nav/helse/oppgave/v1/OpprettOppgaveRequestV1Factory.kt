package no.nav.helse.oppgave.v1

import no.nav.helse.AktoerId
import no.nav.helse.Tema
import no.nav.helse.behandlendeenhet.Enhet
import no.nav.helse.oppgave.*
import no.nav.helse.oppgave.gateway.OpprettOppgaveRequest
import no.nav.helse.oppgave.gateway.Prioritet
import java.time.LocalDate

object OpprettOppgaveRequestV1Factory {

    fun instance(
        behandlendeEnhet: Enhet,
        sokerAktoerId: AktoerId,
        prioritet: Prioritet,
        tema: Tema,
        behandlingsTema: BehandlingsTema,
        temaGruppe: TemaGruppe,
        behandlesAv: FagSystem,
        journalfoertI: FagSystem,
        journalPostId: JournalPostId,
        aktivDato: LocalDate,
        frist: LocalDate,
        oppgaveType: OppgaveType,
        behandlingsType: BehandlingsType
    ) : OpprettOppgaveRequest {
        return OpprettOppgaveRequest(
            tildeltEnhetsnr = behandlendeEnhet.id,
            aktoerId = sokerAktoerId.value,
            prioritet = prioritet.name,
            journalpostId = journalPostId.id,
            journalpostkilde = journalfoertI.kode,
            tema = tema.value,
            behandlesAvApplikasjon = behandlesAv.kode,
            aktivDato = aktivDato,
            fristFerdigstillelse = frist,
            oppgavetype = oppgaveType.value,
            behandlingstema = behandlingsTema.value,
            temagruppe = temaGruppe.value,
            behandlingstype = behandlingsType.value
        )
    }
}