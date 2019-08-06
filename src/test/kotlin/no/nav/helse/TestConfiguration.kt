package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getAzureV1WellKnownUrl
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getAzureV2WellKnownUrl
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getNaisStsWellKnownUrl

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        port : Int = 8080,
        jwkSetUrl : String? = wireMockServer?.getJwksUrl(),
        tokenUrl : String? = wireMockServer?.getTokenUrl(),
        sparkelBaseUrl : String? = wireMockServer?.getSparkelBaseUrl(),
        oppgaveBaseUrl : String? = wireMockServer?.getOppgaveBaseUrl(),
        issuer : String? = wireMockServer?.baseUrl(),
        authorizedSystems : String? = wireMockServer?.getSubject(),
        clientSecret: String? = "foo",
        naisStsAuthoriedClients: Set<String> = setOf("srvpps-prosessering")
    ) : Map<String, String> {
        val map = mutableMapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.sparkel.base_url", "$sparkelBaseUrl"),
            Pair("nav.oppgave.base_url", "$oppgaveBaseUrl")
        )

        if (clientSecret != null) {
            map["nav.auth.clients.0.client_secret"] = clientSecret
        }

        // Clients
        if (wireMockServer != null) {
            map["nav.auth.clients.0.alias"] = "nais-sts"
            map["nav.auth.clients.0.client_id"] = "srvpleiepenger-opp"
            map["nav.auth.clients.0.client_secret"] = "very-secret"
            map["nav.auth.clients.0.discovery_endpoint"] = wireMockServer.getNaisStsWellKnownUrl()
        }

        // Issuers
        if (wireMockServer != null) {
            map["nav.auth.issuers.0.alias"] = "nais-sts"
            map["nav.auth.issuers.0.discovery_endpoint"] = wireMockServer.getNaisStsWellKnownUrl()
            map["nav.auth.nais-sts.authorized_clients"] = naisStsAuthoriedClients.joinToString(", ")
        }

//        if (wireMockServer != null && konfigurerAzureIssuer) {
//            if (pleiepengerJoarkAzureClientId == null) throw IllegalStateException("pleiepengerJoarkAzureClientId må settes når Azure skal konfigureres.")
//
//            map["nav.auth.issuers.1.type"] = "azure"
//            map["nav.auth.issuers.1.alias"] = "azure-v1"
//            map["nav.auth.issuers.1.discovery_endpoint"] = wireMockServer.getAzureV1WellKnownUrl()
//            map["nav.auth.issuers.1.audience"] = pleiepengerJoarkAzureClientId
//            map["nav.auth.issuers.1.azure.require_certificate_client_authentication"] = "true"
//            map["nav.auth.issuers.1.azure.authorized_clients"] = azureAuthorizedClients.joinToString(",")
//
//            map["nav.auth.issuers.2.type"] = "azure"
//            map["nav.auth.issuers.2.alias"] = "azure-v2"
//            map["nav.auth.issuers.2.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()
//            map["nav.auth.issuers.2.audience"] = pleiepengerJoarkAzureClientId
//            map["nav.auth.issuers.2.azure.require_certificate_client_authentication"] = "true"
//            map["nav.auth.issuers.2.azure.authorized_clients"] = azureAuthorizedClients.joinToString(",")
//        }

        return map.toMap()
    }

    fun asArray(map : Map<String, String>) : Array<String>  {
        val list = mutableListOf<String>()
        map.forEach { configKey, configValue ->
            list.add("-P:$configKey=$configValue")
        }
        return list.toTypedArray()
    }
}