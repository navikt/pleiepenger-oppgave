package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        port : Int = 8080,
        jwkSetUrl : String? = wireMockServer?.getJwksUrl(),
        tokenUrl : String? = wireMockServer?.getTokenUrl(),
        sparkelBaseUrl : String? = wireMockServer?.getSparkelBaseUrl(),
        oppgaveBaseUrl : String? = wireMockServer?.getOppgaveBaseUrl(),
        issuer : String? = wireMockServer?.baseUrl(),
        authorizedSystems : String? = wireMockServer?.getSubject()
    ) : Map<String, String>{
        return mapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.authorization.token_url","$tokenUrl"),
            Pair("nav.authorization.jwks_url","$jwkSetUrl"),
            Pair("nav.authorization.issuer","$issuer"),
            Pair("nav.rest_api.authorized_systems","$authorizedSystems"),
            Pair("nav.sparkel.base_url", "$sparkelBaseUrl"),
            Pair("nav.oppgave.base_url", "$oppgaveBaseUrl")
        )
    }

    fun asArray(map : Map<String, String>) : Array<String>  {
        val list = mutableListOf<String>()
        map.forEach { configKey, configValue ->
            list.add("-P:$configKey=$configValue")
        }
        return list.toTypedArray()
    }
}