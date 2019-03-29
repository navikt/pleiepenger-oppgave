package no.nav.helse.oppgave.v1

import io.prometheus.client.Counter
import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.Tema
import no.nav.helse.behandlendeenhet.BehandlendeEnhetService
import no.nav.helse.behandlendeenhet.Enhet
import no.nav.helse.dusseldorf.ktor.core.ParameterType
import no.nav.helse.dusseldorf.ktor.core.Throwblem
import no.nav.helse.dusseldorf.ktor.core.ValidationProblemDetails
import no.nav.helse.dusseldorf.ktor.core.Violation
import no.nav.helse.oppgave.*
import no.nav.helse.oppgave.gateway.OppgaveGateway
import no.nav.helse.oppgave.gateway.Prioritet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneOffset

private val behandlndeEnhetCounter = Counter.build()
    .name("behandlende_enhet_counter")
    .help("Teller på hvilken enhet som blir tildelt oppgaven i Gosys.")
    .labelNames("behandlende_enhet")
    .register()

private val logger: Logger = LoggerFactory.getLogger("nav.OpprettOppgaveV1Service")
private val ONLY_DIGITS = Regex("\\d+")

// https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Applikasjoner
private val GOSYS_FAGSYSTEM = FagSystem("GOSYS", "FS22")
private val JOARK_FAGSYSTEM = FagSystem("JOARK", "AS36")
private val INFOTRYGD_FAGSYSTEM = FagSystem("INFOTRYGD", "IT00")

private val OMSORG_TEMA = Tema("OMS") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Tema
private val PLEIEPENGER_SYKT_BARN_NY_ORDNING_BEHANDLINGS_TEMA = BehandlingsTema("ab0320") // Pleiepenger sykt barn ny ordning fom 011017 - https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstema

private val DIGITAL_SOKNAD_BEHANDLIGNSTYPE = BehandlingsType("ae0227") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstyper TODO: Bruk denne når den er opprettet

// https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Oppgavetyper
private val BEHANDLE_SAK_OPPGAVE_TYPE = OppgaveType("BEH_SAK") // Om vi får automatisk journalføring er dette rett oppgavetype
private val JOURNALFORING_OPPGAVE_TYPE = OppgaveType("JFR") // Så lenge vi ikke har automatisk journalføring er dette rett oppgavetype

private val FAMILIE_TEMA_GRUPPE = TemaGruppe("FMLI") // https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Temagrupper

private val PRIORITET = Prioritet.NORM
private const val FRIST_VIRKEDAGER = 3

class OpprettOppgaveV1Service(
    private val behandlendeEnhetService: BehandlendeEnhetService,
    private val oppgaveGateway : OppgaveGateway
) {
    suspend fun opprettOppgave(
        melding: MeldingV1,
        metaData: MetadataV1
    ) : OppgaveId {
        logger.trace(metaData.toString())
        logger.trace("Oppretter oppgave for JournalpostID ${melding.journalPostId}")
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

        behandlndeEnhetCounter.labels(behandlendeEnhet.metricName()).inc()

        logger.trace("Behandlende enhet for oppgaven blir $behandlendeEnhet")

        val request = OpprettOppgaveRequestV1Factory.instance(
            behandlendeEnhet = behandlendeEnhet,
            sokerAktoerId = sokerAktoerId,
            prioritet = PRIORITET,
            tema = OMSORG_TEMA,
            behandlingsTema = PLEIEPENGER_SYKT_BARN_NY_ORDNING_BEHANDLINGS_TEMA,
            behandlesAv = INFOTRYGD_FAGSYSTEM,
            journalfoertI = JOARK_FAGSYSTEM,
            journalPostId = JournalPostId(melding.journalPostId),
            aktivDato = LocalDate.now(ZoneOffset.UTC),
            frist = DateUtils.nWeekdaysFromToday(FRIST_VIRKEDAGER),
            oppgaveType = JOURNALFORING_OPPGAVE_TYPE,
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
        val violations = mutableListOf<Violation>()
        if (!melding.soker.aktoerId.matches(ONLY_DIGITS)) {
            violations.add(Violation(parameterName = "soker.aktoer_id", reason = "Ugyldig AktørID. Kan kun være siffer.", invalidValue = melding.soker.aktoerId, parameterType = ParameterType.ENTITY))
        }
        if (melding.barn.aktoerId != null && !melding.barn.aktoerId.matches(ONLY_DIGITS)) {
            violations.add(Violation(parameterName = "barn.aktoer_id", reason = "Ugyldig AktørID. Kan kun være siffer.", invalidValue = melding.barn.aktoerId, parameterType = ParameterType.ENTITY))
        }
        if (!melding.journalPostId.matches(ONLY_DIGITS)) {
            violations.add(Violation(parameterName = "journal_post_id", reason = "Ugyldig JournalpostID. Kan kun være siffer.", invalidValue = melding.journalPostId, parameterType = ParameterType.ENTITY))
        }
        if (violations.isNotEmpty()) {
            throw Throwblem(ValidationProblemDetails(violations.toSet()))
        }
    }
}

private fun Enhet.metricName(): String {
    return navn ?: id
}
