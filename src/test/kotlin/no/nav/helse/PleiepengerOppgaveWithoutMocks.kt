package no.nav.helse

import io.ktor.server.testing.withApplication

/**
 *  - Mer leslig loggformat
 *  - Setter proxy settings
 *  - Starter på annen port
 */
class PleiepengerOppgaveWithoutMocks {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            System.setProperty("http.nonProxyHosts", "localhost")
            System.setProperty("http.proxyHost", "127.0.0.1")
            System.setProperty("http.proxyPort", "5001")
            System.setProperty("https.proxyHost", "127.0.0.1")
            System.setProperty("https.proxyPort", "5001")

            // nav.authorization.service_account.client_secret Må fortsatt settes som parameter ved oppstart utenom koden

            val q1Args = TestConfiguration.asArray(TestConfiguration.asMap(
                port = 8113,
                tokenUrl = "https://security-token-service.nais.preprod.local/rest/v1/sts/token",
                sparkelBaseUrl = "https://sparkel.nais.preprod.local",
                oppgaveBaseUrl = "https://oppgave.nais.preprod.local",
                jwkSetUrl = "https://security-token-service.nais.preprod.local/rest/v1/sts/jwks",
                issuer = "https://security-token-service.nais.preprod.local",
                authorizedSystems = "srvpleiepenger-opp"
            ))


            withApplication { no.nav.helse.main(q1Args) }
        }
    }
}
