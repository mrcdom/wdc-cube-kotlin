package br.com.wdc.framework.rest

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.JsonStreamReader
import br.com.wdc.framework.domain.codec.ModelCodec
import br.com.wdc.framework.rest.error.RequestBodyException
import br.com.wdc.framework.rest.error.RequestParameterException
import br.com.wdc.framework.rest.security.RestPrincipal
import io.javalin.http.Context
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

/**
 * O que o handler de uma rota recebe: a requisição, com leitura tipada dos parâmetros e do corpo.
 *
 * **O valor devolvido pelo handler é a resposta:**
 *
 * | Valor | Resposta |
 * |---|---|
 * | `Unit` ou `null` | corpo vazio (o que o handler tiver escrito em [http] vale) |
 * | [RestResponse] | status, cabeçalhos e corpo dela |
 * | [RawJson] | o JSON como está |
 * | `String` | `text/plain` |
 * | `ByteArray`, `InputStream` | `application/octet-stream` (outro tipo: [RestResponse] com `contentType`) |
 * | qualquer outro | DTO, pelo mapeador JSON da API |
 *
 * Parâmetro ausente ou malformado lança [RequestParameterException]; corpo ilegível, [RequestBodyException] —
 * ambos 400 pelo mapeamento padrão.
 */
class RestCall internal constructor(
    val request: RequestContext,
    private val api: RestApi,
) {
    /** A requisição do Javalin, para o que a [RestCall] não cobre. */
    val http: Context get() = request.http

    val principal: RestPrincipal? get() = request.principal

    /** O principal, exigido. Sem ele, `AuthenticationRequiredException` (a resposta de "sem credencial"). */
    inline fun <reified P : RestPrincipal> requirePrincipal(): P =
        request.principal as? P ?: throw br.com.wdc.framework.rest.security.AuthenticationRequiredException("Authentication required")

    /**
     * Executa [block] na transação da requisição (a mesma ponte das rotas `transactional`), para quem precisa
     * demarcar só parte do handler.
     */
    suspend fun <T> inTransaction(block: suspend () -> T): T = api.transactions.run(request, block)

    // :: Status e cabeçalhos da resposta

    fun status(status: Int): RestCall = apply { http.status(status) }

    fun header(name: String, value: String): RestCall = apply { http.header(name, value) }

    // :: Cabeçalhos

    fun header(name: String): String? = request.header(name)

    fun requiredHeader(name: String): String =
        header(name)?.takeIf { it.isNotBlank() } ?: throw RequestParameterException(name, "Required header '$name' is not present")

    // :: Caminho

    fun path(name: String): String =
        request.pathParam(name) ?: throw RequestParameterException(name, "Path parameter '$name' is not present")

    fun pathLong(name: String): Long = convert(name, path(name), "long") { it.toLong() }

    fun pathInt(name: String): Int = convert(name, path(name), "int") { it.toInt() }

    fun pathUuid(name: String): UUID = convert(name, path(name), "UUID") { UUID.fromString(it) }

    // :: Query — `query*` devolve `null` se ausente ou vazio; `required*` exige.

    fun query(name: String): String? = request.queryParam(name)?.takeIf { it.isNotEmpty() }

    fun queries(name: String): List<String> = http.queryParams(name)

    fun requiredQuery(name: String): String =
        query(name) ?: throw RequestParameterException(name, "Required request parameter '$name' is not present")

    fun queryLong(name: String): Long? = query(name)?.let { convert(name, it, "long") { v -> v.toLong() } }

    fun queryInt(name: String): Int? = query(name)?.let { convert(name, it, "int") { v -> v.toInt() } }

    fun queryDouble(name: String): Double? = query(name)?.let { convert(name, it, "double") { v -> v.toDouble() } }

    /** `true`/`false`, sem diferenciar maiúsculas; também `1`/`0`, `yes`/`no`, `on`/`off`, como o Spring. */
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

    /** Pelo nome da constante, exato (como o Spring). */
    inline fun <reified T : Enum<T>> queryEnum(name: String): T? =
        query(name)?.let { value -> enumValues<T>().firstOrNull { it.name == value } ?: throw mismatch(name, value, T::class.java.simpleName) }

    fun <T> query(name: String, typeName: String, parse: (String) -> T): T? = query(name)?.let { convert(name, it, typeName, parse) }

    // :: Corpo

    fun bodyText(): String = http.body()

    fun bodyBytes(): ByteArray = http.bodyAsBytes()

    /** O corpo como DTO, pelo mapeador JSON da API. Corpo vazio é `null` — use [body] para exigi-lo. */
    fun <T> bodyOrNull(type: Type): T? {
        val text = http.body()
        if (text.isBlank()) return null
        return try {
            api.json.fromJsonString<Any>(text, type) as T?
        } catch (e: Exception) {
            throw RequestBodyException("JSON parse error: ${e.message}", e)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T> body(): T = bodyOrNull<T>(typeOf<T>().javaType) ?: throw RequestBodyException("Required request body is missing")

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T> bodyOrNull(): T? = bodyOrNull<T>(typeOf<T>().javaType)

    /** O corpo como leitor de streaming, para os `ModelCodec`. Corpo vazio é lido como `{}`. */
    fun bodyInput(): ExtensibleObjectInput = JsonStreamReader(http.body().ifBlank { "{}" })

    /** O corpo como entidade, pelo codec dela. */
    fun <E> entity(codec: ModelCodec<E, *>): E = readingBody { codec.readEntity(bodyInput()) }

    /** O corpo como entidade para update: a entidade e a projeção dos campos presentes. */
    fun <E> entityForUpdate(codec: ModelCodec<E, *>): ModelCodec.UpdateData<E> = readingBody { codec.readEntityForUpdate(bodyInput()) }

    /** Lê o corpo com [read]; falha de leitura que não seja da aplicação vira [RequestBodyException]. */
    fun <T> readingBody(read: () -> T): T = try {
        read()
    } catch (e: br.com.wdc.framework.domain.exception.BusinessException) {
        throw e
    } catch (e: IllegalStateException) {
        throw RequestBodyException("Malformed JSON body: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw RequestBodyException("Malformed JSON body: ${e.message}", e)
    }

    // :: Apoio

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
