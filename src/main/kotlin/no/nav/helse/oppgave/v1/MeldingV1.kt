package no.nav.helse.oppgave.v1

data class MeldingV1(
    val soker : Soker,
    val barn: Barn,
    val tema: String
)

data class Soker (
    val aktoerId: String
)

data class Barn (
    val aktoerId: String?
)