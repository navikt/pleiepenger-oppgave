package no.nav.helse.behandlendeenhet

import no.nav.helse.AktoerId
import no.nav.helse.CorrelationId
import no.nav.helse.Tema
/*
    return Enhet(id="4432", navn="NAV Arbeid og ytelser Follo")
 */
class BehandlendeEnhetService(
    private val sparkelGateway: SparkelGateway
) {
    suspend fun hentBehandlendeEnhet(
        sokerAktoerId : AktoerId,
        barnAktoerId: AktoerId? = null,
        tema: Tema,
        correlationId: CorrelationId
    ) : Enhet {
        return sparkelGateway.hentBehandlendeEnhet(
            hovedAktoer = sokerAktoerId,
            medAktoerer = if (barnAktoerId == null) listOf() else listOf(barnAktoerId),
            tema = tema,
            correlationId = correlationId
        )
    }
}