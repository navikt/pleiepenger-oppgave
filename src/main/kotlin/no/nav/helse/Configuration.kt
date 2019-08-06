package no.nav.helse

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getOptionalList
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import java.net.URI

private const val NAIS_STS_ALIAS = "nais-sts"

@KtorExperimentalAPI
data class Configuration(private val config : ApplicationConfig) {
    private val clients = config.clients()

    private fun getNaisStsAuthorizedClients(): List<String> {
        return config.getOptionalList(
            key = "nav.auth.nais-sts.authorized_clients",
            builder = { value -> value },
            secret = false
        )
    }

    internal fun getOppgaveBaseUrl() : URI {
        return URI(config.getRequiredString("nav.oppgave.base_url", secret = false))
    }

    internal fun getSparkelBaseUrl() : URI {
        return URI(config.getRequiredString("nav.sparkel.base_url", secret = false))
    }

    internal fun issuers(): Map<Issuer, Set<ClaimRule>> {
        return config.issuers().withAdditionalClaimRules(
            mapOf(NAIS_STS_ALIAS to setOf(StandardClaimRules.Companion.EnforceSubjectOneOf(getNaisStsAuthorizedClients().toSet())))
        )
    }

    internal fun naisStsClient() : ClientSecretClient  {
        val client = clients.getOrElse(NAIS_STS_ALIAS) {
            throw IllegalStateException("Client[$NAIS_STS_ALIAS] må være satt opp.")
        }
        return client as ClientSecretClient
    }
}