package no.nav.helse.journalforing.api

import io.ktor.http.HttpHeaders

class ManglerCorrelationId : IllegalStateException("Mangler header ${HttpHeaders.XCorrelationId}")