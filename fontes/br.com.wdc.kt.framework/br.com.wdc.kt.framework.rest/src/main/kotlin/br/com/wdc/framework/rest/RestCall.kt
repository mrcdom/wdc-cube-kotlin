package br.com.wdc.framework.rest

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.rest.error.RequestBodyException
import br.com.wdc.framework.rest.error.RequestParameterException
import br.com.wdc.framework.rest.security.RestPrincipal
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import io.javalin.http.Context
import java.lang.reflect.Type
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.UUID
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * What a route's handler receives: the request, with typed reading of the parameters and the body.
 *
 * **The value returned by the handler is the response:**
 *
 * | Value | Response |
 * |---|---|
 * | `Unit` or `null` | empty body (whatever the handler wrote to [http] stands) |
 * | [RestResponse] | its status, headers and body |
 * | [RawJson] | the JSON as is |
 * | `String` | `text/plain` |
 * | `ByteArray`, `InputStream` | `application/octet-stream` (other type: [RestResponse] with `contentType`) |
 * | anything else | DTO, through the API's JSON mapper |
 *
 * A missing or malformed parameter throws [RequestParameterException]; an unreadable body, [RequestBodyException] —
 * both 400 by the default mapping.
 */
class RestCall internal constructor(
    val request: RequestContext,
    private val api: RestApi,
) {
    /** The Javalin request, for what [RestCall] doesn't cover. */
    val http: Context get() = request.http

    val principal: RestPrincipal? get() = request.principal

    /** The principal, required. Without it, `AuthenticationRequiredException` (the "no credential" response). */
    inline fun <reified P : RestPrincipal> requirePrincipal(): P =
        request.principal as? P ?: throw br.com.wdc.framework.rest.security.AuthenticationRequiredException("Authentication required")

    /**
     * Runs [block] in the request transaction (the same bridge as `transactional` routes), for handlers that need
     * to demarcate only part of their work.
     */
    suspend fun <T> inTransaction(block: suspend () -> T): T = api.transactions.run(request, block)

    /**
     * Records a measurement for the `Server-Timing` header (see [ServerTimingOptions]): it goes out before `total`, in
     * registration order. Without the option on, it does nothing. It is a plain function: the route can pass it along
     * (`call::timing`) to whatever measures.
     *
     * @param name the measurement name (a token, no spaces: `action`, `db`)
     * @param nanos the duration, in nanoseconds
     * @param desc the description, which only goes out with [ServerTimingOptions.names] on
     */
    fun timing(name: String, nanos: Long, desc: String? = null) {
        request[ServerTiming.KEY]?.add(name, nanos, desc)
    }

    /** Runs [block] and records how long it took as the measurement [name] (see [timing]), even if it fails. */
    inline fun <T> timed(name: String, desc: String? = null, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            timing(name, System.nanoTime() - start, desc)
        }
    }

    // :: Response status and headers

    fun status(status: Int): RestCall = apply { http.status(status) }

    fun header(name: String, value: String): RestCall = apply { http.header(name, value) }

    // :: Headers

    fun header(name: String): String? = request.header(name)

    fun requiredHeader(name: String): String =
        header(name)?.takeIf { it.isNotBlank() } ?: throw RequestParameterException(name, "Required header '$name' is not present")

    /**
     * User and password from `Authorization: Basic …`, the scheme compared case-insensitively. After the Base64,
     * each part is decoded as a form (`%3A`, `+`), which is how OAuth sends `client_id` and `client_secret`
     * (RFC 6749, section 2.3.1). Only the first `:` separates: the password may contain others.
     *
     * @return `null` without the header, with another scheme, with invalid Base64 or encoding, or without `:`
     */
    fun basicCredentials(): Pair<String, String>? {
        val value = header("Authorization")?.trim() ?: return null
        val prefix = "Basic "
        if (!value.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) return null
        return try {
            val decoded = String(Base64.getDecoder().decode(value.substring(prefix.length).trim()), StandardCharsets.UTF_8)
            val colon = decoded.indexOf(':')
            if (colon < 0) return null
            URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8) to
                URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // :: Path

    fun path(name: String): String =
        request.pathParam(name) ?: throw RequestParameterException(name, "Path parameter '$name' is not present")

    fun pathLong(name: String): Long = convert(name, path(name), "long") { it.toLong() }

    fun pathInt(name: String): Int = convert(name, path(name), "int") { it.toInt() }

    fun pathUuid(name: String): UUID = convert(name, path(name), "UUID") { UUID.fromString(it) }

    // :: Query — `query*` returns `null` if absent or empty; `required*` requires it.

    fun query(name: String): String? = request.queryParam(name)?.takeIf { it.isNotEmpty() }

    fun queries(name: String): List<String> = http.queryParams(name)

    fun requiredQuery(name: String): String =
        query(name) ?: throw missingQuery(name)

    fun queryLong(name: String): Long? = query(name)?.let { convert(name, it, "long") { v -> v.toLong() } }

    fun queryInt(name: String): Int? = query(name)?.let { convert(name, it, "int") { v -> v.toInt() } }

    fun queryDouble(name: String): Double? = query(name)?.let { convert(name, it, "double") { v -> v.toDouble() } }

    /** `true`/`false`, case-insensitive; also `1`/`0`, `yes`/`no`, `on`/`off`, as Spring does. */
    fun queryBoolean(name: String): Boolean? = query(name)?.let { value ->
        when (value.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> throw mismatch(name, value, "boolean")
        }
    }

    fun queryBoolean(name: String, default: Boolean): Boolean = queryBoolean(name) ?: default

    fun queryUuid(name: String): UUID? = query(name)?.let { convert(name, it, "UUID") { v -> UUID.fromString(v) } }

    fun queryLocalDate(name: String): LocalDate? = query(name)?.let { convert(name, it, "LocalDate") { v -> LocalDate.parse(v) } }

    fun queryLocalDateTime(name: String): LocalDateTime? =
        query(name)?.let { convert(name, it, "LocalDateTime") { v -> LocalDateTime.parse(v) } }

    /** By the exact constant name (as Spring does). */
    inline fun <reified T : Enum<T>> queryEnum(name: String): T? =
        query(name)?.let { value -> enumValues<T>().firstOrNull { it.name == value } ?: throw mismatch(name, value, T::class.java.simpleName) }

    fun <T> query(name: String, typeName: String, parse: (String) -> T): T? = query(name)?.let { convert(name, it, typeName, parse) }

    // Required: absent, the same failure as [requiredQuery]; malformed, the same as the matching `query*`.

    fun requiredQueryLong(name: String): Long = queryLong(name) ?: throw missingQuery(name)

    fun requiredQueryInt(name: String): Int = queryInt(name) ?: throw missingQuery(name)

    fun requiredQueryDouble(name: String): Double = queryDouble(name) ?: throw missingQuery(name)

    fun requiredQueryBoolean(name: String): Boolean = queryBoolean(name) ?: throw missingQuery(name)

    fun requiredQueryUuid(name: String): UUID = queryUuid(name) ?: throw missingQuery(name)

    fun requiredQueryLocalDate(name: String): LocalDate = queryLocalDate(name) ?: throw missingQuery(name)

    fun requiredQueryLocalDateTime(name: String): LocalDateTime = queryLocalDateTime(name) ?: throw missingQuery(name)

    inline fun <reified T : Enum<T>> requiredQueryEnum(name: String): T = queryEnum<T>(name) ?: throw missingQuery(name)

    // :: Body

    fun bodyText(): String = http.body()

    fun bodyBytes(): ByteArray = http.bodyAsBytes()

    /** The body as a DTO, through the API's JSON mapper. An empty body is `null` — use [body] to require it. */
    fun <T> bodyOrNull(type: Type): T? {
        val text = http.body()
        if (text.isBlank()) return null
        return try {
            api.json.fromJsonString<Any>(text, type) as T?
        } catch (e: br.com.wdc.framework.rest.json.JsonDeclarationException) {
            throw e // a declaration defect, not a request one
        } catch (e: Exception) {
            throw RequestBodyException("JSON parse error: ${e.message}", e)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T> body(): T = bodyOrNull<T>(typeOf<T>().javaType) ?: throw RequestBodyException("Required request body is missing")

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T> bodyOrNull(): T? = bodyOrNull<T>(typeOf<T>().javaType)

    /**
     * The names of the top-level JSON body fields that came with an explicit `null`. A DTO reads absent and null
     * the same way; in a partial update, this separates "leave alone" (absent) from "clear" (null):
     * ```kotlin
     * val patch = call.body<CustomerPatch>()
     * if ("phone" in call.explicitNulls()) customer.phone = null else patch.phone?.let { customer.phone = it }
     * ```
     * An empty, malformed or non-object body gives the empty set. It doesn't consume the body: [body] still works.
     */
    fun explicitNulls(): Set<String> {
        val text = http.body()
        if (text.isBlank()) return emptySet()
        val root = try {
            JsonParser.parseString(text)
        } catch (_: JsonParseException) {
            return emptySet()
        }
        if (!root.isJsonObject) return emptySet()
        return root.asJsonObject.entrySet().filter { it.value.isJsonNull }.mapTo(LinkedHashSet()) { it.key }
    }

    /** The body as a streaming reader, for the `ModelCodec`s. An empty body is read as `{}`. */
    fun bodyInput(): ExtensibleObjectInput = JsonStreamReader(http.body().ifBlank { "{}" })

    /** The body as an entity, through its codec. */
    fun <E> entity(codec: ModelCodec<E, *>): E = readingBody { codec.readEntity(bodyInput()) }

    /** The body as an entity for update: the entity and the projection of the fields present. */
    fun <E> entityForUpdate(codec: ModelCodec<E, *>): ModelCodec.UpdateData<E> = readingBody { codec.readEntityForUpdate(bodyInput()) }

    /** Reads the body with [read]; a read failure that isn't the application's becomes [RequestBodyException]. */
    fun <T> readingBody(read: () -> T): T = try {
        read()
    } catch (e: br.com.wdc.framework.domain.exception.BusinessException) {
        throw e
    } catch (e: IllegalStateException) {
        throw RequestBodyException("Malformed JSON body: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw RequestBodyException("Malformed JSON body: ${e.message}", e)
    }

    // :: Support

    @PublishedApi
    internal fun missingQuery(name: String) =
        RequestParameterException(name, "Required request parameter '$name' is not present")

    @PublishedApi
    internal fun mismatch(name: String, value: String, typeName: String) =
        RequestParameterException(name, "Failed to convert value '$value' of parameter '$name' to required type '$typeName'")

    private fun <T> convert(name: String, value: String, typeName: String, parse: (String) -> T): T = try {
        parse(value)
    } catch (_: NumberFormatException) {
        throw mismatch(name, value, typeName)
    } catch (_: IllegalArgumentException) {
        throw mismatch(name, value, typeName)
    } catch (_: DateTimeParseException) {
        throw mismatch(name, value, typeName)
    }
}
