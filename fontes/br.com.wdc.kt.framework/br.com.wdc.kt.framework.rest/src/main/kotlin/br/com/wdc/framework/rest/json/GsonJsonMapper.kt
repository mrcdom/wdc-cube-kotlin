package br.com.wdc.framework.rest.json

import com.google.gson.FieldNamingStrategy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.javalin.json.JsonMapper
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Serialização dos DTOs (o que não é entidade de repositório) com o Gson, configurável para reproduzir uma
 * resposta existente: política de nulos global e por classe ou campo ([JsonNulls]), formato de cada tipo de
 * data ([DateFormats]) e ajustes livres do Gson ([JsonOptions.customize]).
 *
 * Implementa o `JsonMapper` do Javalin, e por isso também serve a `ctx.json(…)`/`ctx.bodyAsClass(…)` se a
 * aplicação o instalar com `config.jsonMapper(…)`.
 *
 * Por campo, o formato de data se troca com `@JsonAdapter` do próprio Gson.
 *
 * Classes Kotlin são lidas pelo construtor primário ([KotlinConstructorFactory]): os padrões dos parâmetros valem e
 * campo `val` não é gravado por reflexão — o que o JDK 26 avisa que vai bloquear.
 */
class GsonJsonMapper(val options: JsonOptions = JsonOptions()) : JsonMapper {

    val gson: Gson = build(options)

    override fun toJsonString(obj: Any, type: Type): String = gson.toJson(obj, type)

    override fun <T : Any> fromJsonString(json: String, targetType: Type): T = gson.fromJson(json, targetType)

    fun toJson(value: Any?): String = if (value == null) "null" else gson.toJson(value)

    inline fun <reified T> fromJson(json: String): T = gson.fromJson(json, object : TypeToken<T>() {}.type)

    private companion object {

        fun build(options: JsonOptions): Gson {
            val builder = GsonBuilder().setFieldNamingStrategy(options.fieldNaming)
            if (options.nulls == NullPolicy.INCLUDE) builder.serializeNulls()
            if (!options.htmlEscaping) builder.disableHtmlEscaping()
            if (options.prettyPrinting) builder.setPrettyPrinting()

            val dates = options.dates
            dates.localDate?.let { builder.registerTypeAdapter(LocalDate::class.java, TemporalAdapter(it, LocalDate::from).nullSafe()) }
            dates.localDateTime?.let { f ->
                val parse: (TemporalAccessor) -> LocalDateTime = LocalDateTime::from
                builder.registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter(f, dates.lenientLocalDateTime, parse).nullSafe())
            }
            dates.localTime?.let { builder.registerTypeAdapter(LocalTime::class.java, TemporalAdapter(it, LocalTime::from).nullSafe()) }
            dates.offsetDateTime?.let { builder.registerTypeAdapter(OffsetDateTime::class.java, TemporalAdapter(it, OffsetDateTime::from).nullSafe()) }
            dates.zonedDateTime?.let { builder.registerTypeAdapter(ZonedDateTime::class.java, TemporalAdapter(it, ZonedDateTime::from).nullSafe()) }
            dates.instant?.let { builder.registerTypeAdapter(Instant::class.java, TemporalAdapter(it, Instant::from).nullSafe()) }
            dates.utilDatePattern?.let { builder.registerTypeAdapter(Date::class.java, UtilDateAdapter(it, dates.utilDateZone).nullSafe()) }

            // a ordem importa: a fábrica registrada depois é consultada antes; a de nulos delega a leitura a esta
            builder.registerTypeAdapterFactory(KotlinConstructorFactory(options.fieldNaming))
            builder.registerTypeAdapterFactory(NullPolicyFactory(options.nulls, options.fieldNaming))
            options.customizers.forEach { it(builder) }
            return builder.create()
        }
    }

    private class TemporalAdapter<T : TemporalAccessor>(
        private val formatter: DateTimeFormatter,
        private val from: (TemporalAccessor) -> T,
    ) : TypeAdapter<T>() {
        override fun write(out: JsonWriter, value: T) {
            out.value(formatter.format(value))
        }

        override fun read(input: JsonReader): T = parse(input.nextString())

        fun parse(text: String): T = try {
            formatter.parse(text, from)
        } catch (e: DateTimeParseException) {
            throw JsonParseException("Data inválida: $text", e)
        }
    }

    private class LocalDateTimeAdapter(
        private val formatter: DateTimeFormatter,
        private val lenient: Boolean,
        private val from: (TemporalAccessor) -> LocalDateTime,
    ) : TypeAdapter<LocalDateTime>() {
        override fun write(out: JsonWriter, value: LocalDateTime) {
            out.value(formatter.format(value))
        }

        override fun read(input: JsonReader): LocalDateTime {
            val text = input.nextString()
            return try {
                formatter.parse(text, from)
            } catch (e: DateTimeParseException) {
                if (!lenient) throw JsonParseException("Data inválida: $text", e)
                try {
                    OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
                } catch (_: DateTimeParseException) {
                    throw JsonParseException("Data inválida: $text", e)
                }
            }
        }
    }

    private class UtilDateAdapter(pattern: String, private val zone: ZoneId) : TypeAdapter<Date>() {
        private val formatter = DateTimeFormatter.ofPattern(pattern)

        override fun write(out: JsonWriter, value: Date) {
            out.value(formatter.format(value.toInstant().atZone(zone)))
        }

        override fun read(input: JsonReader): Date {
            if (input.peek() == JsonToken.NUMBER) {
                return Date(input.nextLong())
            }
            val text = input.nextString()
            return try {
                Date.from(Instant.from(formatter.withZone(zone).parse(text)))
            } catch (e: DateTimeParseException) {
                try {
                    Date.from(OffsetDateTime.parse(text).toInstant())
                } catch (_: DateTimeParseException) {
                    throw JsonParseException("Data inválida: $text", e)
                }
            }
        }
    }

    /**
     * Aplica [JsonNulls] onde ele diverge da política global. Só envolve as classes que têm a anotação; as
     * outras seguem direto pelo adaptador do Gson.
     *
     * A classe envolvida é escrita como árvore com todos os nulos e depois podada: some o campo nulo cuja
     * política efetiva é [NullPolicy.OMIT]. Ressalva: um `Map` dentro dessa classe sai com os valores nulos.
     */
    private class NullPolicyFactory(private val global: NullPolicy, private val naming: FieldNamingStrategy) : TypeAdapterFactory {

        private val plans = ConcurrentHashMap<Class<*>, Map<String, NullPolicy>?>()

        override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val raw = type.rawType
            if (raw.isPrimitive || raw.isArray || raw.isEnum || raw.name.startsWith("java.") || raw.name.startsWith("kotlin.")) {
                return null
            }
            val plan = plans.computeIfAbsent(raw) { planOf(it) } ?: return null
            val delegate = gson.getDelegateAdapter(this, type)
            val elements = gson.getAdapter(JsonElement::class.java)
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T?) {
                    if (value == null) {
                        out.nullValue()
                        return
                    }
                    val tree = delegate.toJsonTree(value)
                    if (tree is JsonObject) {
                        for (name in tree.keySet().toList()) {
                            if (tree.get(name).isJsonNull && (plan[name] ?: global) == NullPolicy.OMIT) {
                                tree.remove(name)
                            }
                        }
                    }
                    val serializeNulls = out.serializeNulls
                    out.serializeNulls = true
                    try {
                        elements.write(out, tree)
                    } finally {
                        out.serializeNulls = serializeNulls
                    }
                }

                override fun read(input: JsonReader): T? = delegate.read(input)
            }
        }

        /** Nome no JSON → política, para cada campo; `null` se a classe não tem a anotação. */
        private fun planOf(type: Class<*>): Map<String, NullPolicy>? {
            val fields = ArrayList<Field>()
            var current: Class<*>? = type
            while (current != null && current != Any::class.java) {
                current.declaredFields.filterTo(fields) { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) }
                current = current.superclass
            }
            val classPolicy = type.getAnnotation(JsonNulls::class.java)?.value
            if (classPolicy == null && fields.none { it.isAnnotationPresent(JsonNulls::class.java) }) {
                return null
            }
            val plan = HashMap<String, NullPolicy>()
            for (field in fields) {
                val name = field.getAnnotation(SerializedName::class.java)?.value ?: naming.translateName(field)
                plan[name] = field.getAnnotation(JsonNulls::class.java)?.value ?: classPolicy ?: global
            }
            return plan
        }
    }
}
