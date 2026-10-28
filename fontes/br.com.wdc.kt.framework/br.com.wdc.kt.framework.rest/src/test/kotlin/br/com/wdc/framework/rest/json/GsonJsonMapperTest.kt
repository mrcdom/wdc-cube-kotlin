package br.com.wdc.framework.rest.json

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class GsonJsonMapperTest {

    data class Plain(val name: String?, val note: String?)

    @JsonNulls(NullPolicy.INCLUDE)
    data class AlwaysNulls(val name: String?, @JsonNulls(NullPolicy.OMIT) val hidden: String?)

    data class FieldLevel(val a: String?, @JsonNulls(NullPolicy.INCLUDE) val b: String?, @SerializedName("c_c") @JsonNulls(NullPolicy.INCLUDE) val c: String?)

    data class Nested(val inner: AlwaysNulls?, val other: String?)

    data class Dates(
        val day: LocalDate,
        val moment: LocalDateTime,
        val precise: LocalDateTime,
        val instant: Instant,
        val offset: OffsetDateTime,
        val legacy: Date,
    )

    class BrazilianDate : TypeAdapter<LocalDate>() {
        private val f = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        override fun write(out: JsonWriter, value: LocalDate?) {
            if (value == null) out.nullValue() else out.value(f.format(value))
        }
        override fun read(input: JsonReader): LocalDate = LocalDate.parse(input.nextString(), f)
    }

    data class PerField(val iso: LocalDate, @JsonAdapter(BrazilianDate::class) val br: LocalDate)

    data class Values(val amount: BigDecimal, val key: UUID, val kind: Kind, val html: String)

    enum class Kind { A, @SerializedName("bee") B }

    data class AtHolder(val at: LocalDateTime)

    data class DayHolder(val d: LocalDate)

    @Test
    fun nulls_globalPolicy() {
        assertEquals("""{"name":"x"}""", GsonJsonMapper().toJson(Plain("x", null)))
        assertEquals("""{"name":"x","note":null}""", GsonJsonMapper(JsonOptions.springDefaults()).toJson(Plain("x", null)))
    }

    @Test
    fun nulls_classAndFieldAnnotations_overrideTheGlobalPolicy() {
        val omit = GsonJsonMapper()
        assertEquals("""{"name":null}""", omit.toJson(AlwaysNulls(null, null)))
        assertEquals("""{"b":null,"c_c":null}""", omit.toJson(FieldLevel(null, null, null)))
        assertEquals("""{"inner":{"name":null}}""", omit.toJson(Nested(AlwaysNulls(null, null), null)))

        val include = GsonJsonMapper(JsonOptions.springDefaults())
        assertEquals("""{"name":null}""", include.toJson(AlwaysNulls(null, null)))
        assertEquals("""{"inner":null,"other":null}""", include.toJson(Nested(null, null)))
    }

    @Test
    fun dates_followJacksonDefaults() {
        val mapper = GsonJsonMapper(JsonOptions.springDefaults())
        val value = Dates(
            day = LocalDate.of(2026, 9, 27),
            moment = LocalDateTime.of(2026, 9, 27, 9, 0),
            precise = LocalDateTime.of(2026, 9, 27, 9, 0, 1, 500_000_000),
            instant = Instant.parse("2026-09-27T12:00:00Z"),
            offset = OffsetDateTime.of(2026, 9, 27, 9, 0, 0, 0, ZoneOffset.ofHours(-3)),
            legacy = Date.from(Instant.parse("2026-09-27T12:00:00Z")),
        )
        val json = mapper.toJson(value)
        assertEquals(
            """{"day":"2026-09-27","moment":"2026-09-27T09:00:00","precise":"2026-09-27T09:00:01.5","instant":"2026-09-27T12:00:00Z",""" +
                """"offset":"2026-09-27T09:00:00-03:00","legacy":"2026-09-27T12:00:00.000+00:00"}""",
            json,
        )
        assertEquals(value, mapper.fromJson<Dates>(json))
    }

    @Test
    fun localDateTime_acceptsAnInstantWithZ_asUtc() {
        val mapper = GsonJsonMapper()
        assertEquals(LocalDateTime.of(2026, 1, 2, 15, 0), mapper.fromJson<AtHolder>("""{"at":"2026-01-02T12:00:00-03:00"}""").at)
        assertEquals(LocalDateTime.of(2026, 1, 2, 12, 0), mapper.fromJson<AtHolder>("""{"at":"2026-01-02T12:00:00Z"}""").at)
    }

    @Test
    fun dates_globalFormat_andPerFieldAdapter() {
        val options = JsonOptions().apply { dates.localDate = DateTimeFormatter.ofPattern("yyyyMMdd") }
        val mapper = GsonJsonMapper(options)
        val value = PerField(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2))
        assertEquals("""{"iso":"20260102","br":"02/01/2026"}""", mapper.toJson(value))
        assertEquals(value, mapper.fromJson<PerField>("""{"iso":"20260102","br":"02/01/2026"}"""))
    }

    @Test
    fun numbers_uuids_enums_andHtml() {
        val key = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val json = GsonJsonMapper().toJson(Values(BigDecimal("10.50"), key, Kind.B, "<a&b>"))
        assertEquals("""{"amount":10.50,"key":"$key","kind":"bee","html":"<a&b>"}""", json)
    }

    @Test
    fun customize_winsOverTheLibraryAdapters() {
        val options = JsonOptions().apply {
            customize { it.registerTypeAdapter(LocalDate::class.java, BrazilianDate()) }
        }
        assertEquals("""{"d":"02/01/2026"}""", GsonJsonMapper(options).toJson(DayHolder(LocalDate.of(2026, 1, 2))))
    }
}
