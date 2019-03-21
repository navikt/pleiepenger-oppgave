# pleiepenger-oppgave

Inneholder integrasjon mot oppgave for å opprette en gosys-oppgave i forbindelse med søknad om Pleiepenger.
Skal konsumere fra kafka-topic og opprette oppgave.
Kan også sende samme request som kommer på kafka-topic som et REST API-kall til tjenesten.

## Versjon 1
### Meldingsformat
- "aktoer_id" for "barn" må ikke settes. Ved å sette den forsikrer tjenesten at oppgaven sendes til riktig NAV-kontor (mtp. kode 6). Om barnet ikke har noen AktørID sendes det til NAV-kontor kun basert på søkeren.

```json
{
	"soker": {
		"aktoer_id": "1831212532188"
	},
	"barn": {
		"aktoer_id": "1831212532189"
	},
	"journal_post_id": "439772720"
}
```

### Metadata
#### Correlation ID vs Request ID
Correlation ID blir propagert videre, og har ikke nødvendigvis sitt opphav hos konsumenten
Request ID blir ikke propagert videre, og skal ha sitt opphav hos konsumenten

#### REST API
- Correlation ID må sendes som header 'X-Correlation-ID'
- Request ID kan sendes som heder 'X-Request-ID'
- Versjon på meldingen avledes fra pathen '/v1/oppgave' -> 1


#### Kafka
- Correlation ID må sendes som header til meldingen med navn 'X-Correlation-Id'
- Request ID kan sendes som header til meldingen med navn 'X-Correlation-Id'
- Versjon på meldingen må sendes som header til meldingen med navn 'X-Nav-Message-Version'

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #område-helse.
