package br.com.wdc.framework.rest.json

import com.google.gson.FieldNamingPolicy
import com.google.gson.FieldNamingStrategy
import com.google.gson.GsonBuilder
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What to do with a DTO's `null` field in the response. */
enum class NullPolicy {
    /** Writes `"field": null` — the Jackson and Spring Boot default. */
    INCLUDE,

    /** Omits the field — the Gson default. */
    OMIT,
}

/**
 * Null policy of a class or field, overriding [JsonOptions.nulls]. The field's wins over the class's. In Kotlin,
 * on a constructor parameter, it applies to the field without `@field:`.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonNulls(val value: NullPolicy)

/**
 * Date and time formats. Each formatter writes and reads; `null` = the type has no adapter (Gson handles it
 * by reflection, which is almost never what you want).
 *
 * The default reproduces Jackson with `WRITE_DATES_AS_TIMESTAMPS` off: ISO-8601, fraction of a second only when
 * there are nanos (`2026-09-27T09:00:00`).
 */
class DateFormats {
    var localDate: DateTimeFormatter? = DateTimeFormatter.ISO_LOCAL_DATE
    var localDateTime: DateTimeFormatter? = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    var localTime: DateTimeFormatter? = DateTimeFormatter.ISO_LOCAL_TIME
    var offsetDateTime: DateTimeFormatter? = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    var zonedDateTime: DateTimeFormatter? = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    var instant: DateTimeFormatter? = DateTimeFormatter.ISO_INSTANT

    /** `java.util.Date`: the pattern (of `DateTimeFormatter`) and the zone it is written in. The default is Jackson's (`…SSS+00:00`, UTC). */
    var utilDatePattern: String? = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx"
    var utilDateZone: ZoneId = ZoneOffset.UTC

    /**
     * When reading `LocalDateTime`, also accepts an instant with `Z` or an offset (`…T12:00:00Z`),
     * converted to UTC — like Jackson's deserializer.
     */
    var lenientLocalDateTime: Boolean = true
}

/** Configuration of the [GsonJsonMapper]. */
class JsonOptions {

    var nulls: NullPolicy = NullPolicy.OMIT

    val dates = DateFormats()

    /** Field names in the JSON. `@SerializedName` wins. */
    var fieldNaming: FieldNamingStrategy = FieldNamingPolicy.IDENTITY

    /** Gson escapes `<`, `>`, `&` and `=` by default; Jackson doesn't. */
    var htmlEscaping: Boolean = false

    var prettyPrinting: Boolean = false

    internal val customizers = ArrayList<(GsonBuilder) -> Unit>()

    /**
     * Free Gson tuning: type adapters, factories, exclusions. Runs after the library's adapters, and what it
     * registers wins over the library's for the same type.
     */
    fun customize(block: (GsonBuilder) -> Unit) {
        customizers += block
    }

    companion object {
        /** What Spring Boot 2/3 writes without configuration: nulls present, ISO dates. */
        fun springDefaults(): JsonOptions = JsonOptions().apply { nulls = NullPolicy.INCLUDE }
    }
}
