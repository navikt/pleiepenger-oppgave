package no.nav.helse.oppgave.api

import io.ktor.http.HttpHeaders

class ManglerCorrelationId : IllegalStateException("Mangler header ${HttpHeaders.XCorrelationId}")