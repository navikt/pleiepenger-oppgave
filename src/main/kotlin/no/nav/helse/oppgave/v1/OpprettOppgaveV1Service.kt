package no.nav.helse.oppgave.v1

import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.Tema
import no.nav.helse.behandlendeenhet.BehandlendeEnhetService
import no.nav.helse.oppgave.*
import no.nav.helse.oppgave.gateway.OppgaveGateway
import no.nav.helse.oppgave.gateway.Prioritet
import no.nav.helse.validering.Brudd
import no.nav.helse.validering.Valideringsfeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneOffset

private val logger: Logger = LoggerFactory.getLogger("nav.OpprettOppgaveV1Service")
private val ONLY_DIGITS = Regex("\\d+")

private val GOSYS_FAGSYSTEM = FagSystem("GOSYS", "FS22") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Applikasjoner
private val JOARK_FAGSYSTEM = FagSystem("JOARK", "AS36") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Applikasjoner

private val OMSORG_TEMA = Tema("OMS") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Tema
private val PLEIEPENGER_SYKT_BARN_BEHANDLINGS_TEMA = BehandlingsTema("ab0069") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstema
//private val SOKNAD_BEHANDLIGNSTYPE = BehandlingsType("ae0034") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstyper
private val BEHANDLE_SAK_MANUELT_OPPGAVE_TYPE = OppgaveType("BEH_SAK_MK") // TODO: Vi trenger vår egent oppgavetype, https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Oppgavetyper
private val FAMILIE_TEMA_GRUPPE = TemaGruppe("FMLI") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Temagrupper

private val PRIORITET = Prioritet.NORM
private val MAPPE = Mappe(id = "100000095")
private val FRIST_VIRKEDAGER = 3

class OpprettOppgaveV1Service(
    private val behandlendeEnhetService: BehandlendeEnhetService,
    private val oppgaveGateway : OppgaveGateway
) {
    suspend fun opprettOppgave(
        melding: MeldingV1,
        metaData: MetadataV1
    ) : OppgaveId {
        logger.trace(metaData.toString())
        logger.trace("Oppretter oppgave for SakID ${melding.sakId} med JournalpostID ${melding.journalPostId}")
        validerMelding(melding)

        val correlationId = CorrelationId(metaData.correlationId)

        logger.trace("Henter behandlende enhet.")
        logger.trace("Søkers AktørID er ${melding.soker.aktoerId}")
        val sokerAktoerId = AktoerId(melding.soker.aktoerId)
        val barnAktoerId = if (melding.barn.aktoerId != null) AktoerId(melding.barn.aktoerId) else null

        if (barnAktoerId != null) {
            logger.trace("Henter behandlende enhet også med Barnets AktørID som er ${melding.barn.aktoerId}")
        }


        val behandlendeEnhet = behandlendeEnhetService.hentBehandlendeEnhet(
            sokerAktoerId = sokerAktoerId,
            barnAktoerId = barnAktoerId,
            tema = OMSORG_TEMA,
            correlationId = correlationId
        )

        logger.trace("Behandlende enhet for oppgaven blir $behandlendeEnhet")

        val request = OpprettOppgaveRequestV1Factory.instance(
            behandlendeEnhet = behandlendeEnhet,
            sokerAktoerId = sokerAktoerId,
            prioritet = PRIORITET,
            mappe = MAPPE,
            tema = OMSORG_TEMA,
            behandlingsTema = PLEIEPENGER_SYKT_BARN_BEHANDLINGS_TEMA,
            behandlesAv = GOSYS_FAGSYSTEM,
            journalfoertI = JOARK_FAGSYSTEM,
            journalPostId = JournalPostId(melding.journalPostId),
            aktivDato = LocalDate.now(ZoneOffset.UTC),
            frist = DateUtils.nWeekdaysFromToday(FRIST_VIRKEDAGER),
            sakId = SakId(melding.sakId),
            oppgaveType = BEHANDLE_SAK_MANUELT_OPPGAVE_TYPE,
            temaGruppe = FAMILIE_TEMA_GRUPPE
        )

        logger.trace("Sender melding for å opprette oppgave")

        val response = oppgaveGateway.opprettOppgave(
            request = request,
            correlationId = correlationId
        )

        logger.trace("Oppgave oppretett med OppgaveID ${response.id}")

        return OppgaveId(id = response.id)
    }

    private fun validerMelding(melding: MeldingV1) {
        val brudd = mutableListOf<Brudd>()
        if (!melding.soker.aktoerId.matches(ONLY_DIGITS)) {
            brudd.add(Brudd(parameter = "soker.aktoer_id", error = "${melding.soker.aktoerId} er ikke en gyldig AktørID. Kan kun være siffer."))
        }
        if (melding.barn.aktoerId != null && !melding.barn.aktoerId.matches(ONLY_DIGITS)) {
            brudd.add(Brudd(parameter = "barn.aktoer_id", error = "${melding.barn.aktoerId} er ikke en gyldig AktørID. Kan kun være siffer."))
        }
        if (!melding.sakId.matches(ONLY_DIGITS)) {
            brudd.add(Brudd(parameter = "sak_id", error = "${melding.sakId} er ikke en gyldig SakID. Kan kun være siffer."))
        }
        if (!melding.journalPostId.matches(ONLY_DIGITS)) {
            brudd.add(Brudd(parameter = "journal_post_id", error = "${melding.journalPostId} er ikke en gyldig JournalpostID. Kan kun være siffer."))
        }
        if (brudd.isNotEmpty()) {
            throw Valideringsfeil(brudd)
        }
    }
}