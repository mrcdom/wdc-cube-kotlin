package br.com.wdc.framework.rest.json

import com.google.gson.FieldNamingPolicy
import com.google.gson.FieldNamingStrategy
import com.google.gson.GsonBuilder
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** O que fazer com campo `null` de um DTO na resposta. */
enum class NullPolicy {
    /** Escreve `"campo": null` — o padrão do Jackson e do Spring Boot. */
    INCLUDE,

    /** Omite o campo — o padrão do Gson. */
    OMIT,
}

/**
 * Política de nulos de uma classe ou de um campo, sobrepondo a de [JsonOptions.nulls]. A do campo vence a da
 * classe. Em Kotlin, num parâmetro de construtor, vale para o campo sem `@field:`.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonNulls(val value: NullPolicy)

/**
 * Formatos das datas e horas. Cada formatador escreve e lê; `null` = o tipo não tem adaptador (o Gson o trata
 * por reflexão, o que quase nunca é o desejado).
 *
 * O padrão reproduz o Jackson com `WRITE_DATES_AS_TIMESTAMPS` desligado: ISO-8601, fração de segundo só quando
 * há nanos (`2026-09-27T09:00:00`).
 */
class DateFormats {
    var localDate: DateTimeFormatter? = DateTimeFormatter.ISO_LOCAL_DATE
    var localDateTime: DateTimeFormatter? = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    var localTime: DateTimeFormatter? = DateTimeFormatter.ISO_LOCAL_TIME
    var offsetDateTime: DateTimeFormatter? = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    var zonedDateTime: DateTimeFormatter? = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    var instant: DateTimeFormatter? = DateTimeFormatter.ISO_INSTANT

    /** `java.util.Date`: o padrão (de `DateTimeFormatter`) e o fuso em que é escrito. O padrão é o do Jackson (`…SSS+00:00`, UTC). */
    var utilDatePattern: String? = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx"
    var utilDateZone: ZoneId = ZoneOffset.UTC

    /**
     * Na leitura de `LocalDateTime`, aceita também o instante com `Z` ou deslocamento (`…T12:00:00Z`),
     * convertido para UTC — como o desserializador do Jackson.
     */
    var lenientLocalDateTime: Boolean = true
}

/** Configuração do [GsonJsonMapper]. */
class JsonOptions {

    var nulls: NullPolicy = NullPolicy.OMIT

    val dates = DateFormats()

    /** Nome dos campos no JSON. `@SerializedName` vence. */
    var fieldNaming: FieldNamingStrategy = FieldNamingPolicy.IDENTITY

    /** O Gson escapa `<`, `>`, `&` e `=` por padrão; o Jackson não. */
    var htmlEscaping: Boolean = false

    var prettyPrinting: Boolean = false

    internal val customizers = ArrayList<(GsonBuilder) -> Unit>()

    /**
     * Ajuste livre do Gson: adaptadores de tipo, fábricas, exclusões. Roda depois dos adaptadores da biblioteca,
     * e o que registrar vence o dela para o mesmo tipo.
     */
    fun customize(block: (GsonBuilder) -> Unit) {
        customizers += block
    }

    companion object {
        /** O que o Spring Boot 2/3 escreve sem configuração: nulos presentes, datas ISO. */
        fun springDefaults(): JsonOptions = JsonOptions().apply { nulls = NullPolicy.INCLUDE }
    }
}
