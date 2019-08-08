package no.nav.helse

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import java.net.URI

private const val NAIS_STS_ALIAS = "nais-sts"

@KtorExperimentalAPI
data class Configuration(private val config : ApplicationConfig) {
    private val clients = config.clients()

    internal fun getOppgaveBaseUrl() : URI {
        return URI(config.getRequiredString("nav.oppgave.base_url", secret = false))
    }

    internal fun getSparkelBaseUrl() : URI {
        return URI(config.getRequiredString("nav.sparkel.base_url", secret = false))
    }

    internal fun issuers() = config.issuers().withoutAdditionalClaimRules()

    internal fun naisStsClient() : ClientSecretClient  {
        val client = clients.getOrElse(NAIS_STS_ALIAS) {
            throw IllegalStateException("Client[$NAIS_STS_ALIAS] må være satt opp.")
        }
        return client as ClientSecretClient
    }
}