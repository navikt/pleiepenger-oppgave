package no.nav.helse.behandlendeenhet

import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.Tema
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BehandlendeEnhetService(
    private val sparkelGateway: SparkelGateway
) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger("nav.BehandlendeEnhetService")
    }

    suspend fun hentBehandlendeEnhet(
        sokerAktoerId : AktoerId,
        barnAktoerId: AktoerId? = null,
        tema: Tema,
        correlationId: CorrelationId
    ) : Enhet {
        return try {
            sparkelGateway.hentBehandlendeEnhet(
                hovedAktoer = sokerAktoerId,
                medAktoerer = if (barnAktoerId == null) listOf() else listOf(barnAktoerId),
                tema = tema,
                correlationId = correlationId
            )
        } catch (ex: IllegalStateException) {
            if (barnAktoerId != null) {
                logger.warn("Feil ved hentinga av behandlende enhet for både søker og barn. Forsøker å hente kun for søker.")
                sparkelGateway.hentBehandlendeEnhet(
                    hovedAktoer = sokerAktoerId,
                    medAktoerer = listOf(),
                    tema = tema,
                    correlationId = correlationId
                )
            } else {
                throw ex
            }
        }
    }
}