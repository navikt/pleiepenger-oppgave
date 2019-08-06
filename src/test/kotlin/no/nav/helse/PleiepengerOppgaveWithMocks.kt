package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.server.testing.withApplication
import no.nav.helse.dusseldorf.ktor.testsupport.asArguments
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.WireMockBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PleiepengerOppgaveWithMocks {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(PleiepengerOppgaveWithMocks::class.java)


        @JvmStatic
        fun main(args: Array<String>) {

            val wireMockServer: WireMockServer = WireMockBuilder()
                .withPort(8121)
                .withAzureSupport()
                .withNaisStsSupport()
                .build()
                .stubSparkelReady()
                .stubOppgaveReady()

            val testArgs = TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                port = 8122
            ).asArguments()

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    logger.info("Tearing down")
                    wireMockServer.stop()
                    logger.info("Tear down complete")
                }
            })

            withApplication { no.nav.helse.main(testArgs) }
        }
    }
}