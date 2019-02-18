package no.nav.helse.oppgave.v1

import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.ObjectMapper
import no.nav.helse.Tema
import no.nav.helse.behandlendeenhet.BehandlendeEnhetService
import no.nav.helse.oppgave.*
import no.nav.helse.oppgave.gateway.OppgaveGateway
import no.nav.helse.oppgave.gateway.Prioritet
import no.nav.helse.validering.Brudd
import no.nav.helse.validering.Valideringsfeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.OpprettOppgaveV1Service")

private val GOSYS_FAGSYSTEM = FagSystem("GOSYS", "FS22") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Applikasjoner
private val JOARK_FAGSYSTEM = FagSystem("JOARK", "AS36") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Applikasjoner

private val OMSORG_TEMA = Tema("OMS") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Tema
private val PLEIEPENGER_SYKT_BARN_BEHANDLINGS_TEMA = BehandlingsTema("ab0069") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstema
private val SOKNAD_BEHANDLIGNSTYPE = BehandlingsType("ae0034") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstyper
private val BEHANDLE_SAK_MANUELT_OPPGAVE_TYPE = OppgaveType("BEH_SAK_MK ") // TODO: Vi trenger vår egent oppgavetype, https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Oppgavetyper
private val FAMILIE_TEMA_GRUPPE = TemaGruppe("FMLI ") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Temagrupper

private val PRIORITET = Prioritet.NORM
private val MAPPE = Mappe(id = "100000095")
private val AKTIV_VIRKEDAGER = 3
private val FRIST_VIRKEDAGER = 10

class OpprettOppgaveV1Service(
    private val behandlendeEnhetService: BehandlendeEnhetService,
    private val oppgaveGateway : OppgaveGateway
) {
    suspend fun opprettOppgave(
        melding: MeldingV1,
        metaData: MetadataV1
    ) : OppgaveId {
        logger.info(metaData.toString())
        val correlationId = CorrelationId(metaData.correlationId)

        validerMelding(melding)
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

        logger.trace("Behandlende enhet for sak ${melding.sakId} med journal post ${melding.journalPostId} er $behandlendeEnhet")

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
            aktivDato = DateUtils.nWeekdaysFromToday(AKTIV_VIRKEDAGER),
            frist = DateUtils.nWeekdaysFromToday(FRIST_VIRKEDAGER),
            sakId = SakId(melding.sakId),
            oppgaveType = BEHANDLE_SAK_MANUELT_OPPGAVE_TYPE,
            behandlingsType = SOKNAD_BEHANDLIGNSTYPE,
            temaGruppe = FAMILIE_TEMA_GRUPPE
        )

        logger.trace("Sender melding for å opprette oppgave")
        logger.info(ObjectMapper.sparkelOgOppgave().writeValueAsString(request))

        val response = oppgaveGateway.opprettOppgave(
            request = request,
            correlationId = correlationId
        )

        logger.trace("Oppgave oppretett med OppgaveID ${response.id}")

        return OppgaveId(id = response.id)
    }

    private fun validerMelding(melding: MeldingV1) {
        val brudd = mutableListOf<Brudd>()
        // TODO: Validering som bør gjøres?
        if (brudd.isNotEmpty()) {
            throw Valideringsfeil(brudd)
        }
    }
}