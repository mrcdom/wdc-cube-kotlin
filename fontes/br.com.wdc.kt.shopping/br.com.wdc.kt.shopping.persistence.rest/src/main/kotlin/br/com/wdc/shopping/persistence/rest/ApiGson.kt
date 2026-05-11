package br.com.wdc.shopping.persistence.rest

import br.com.wdc.shopping.domain.model.Product
import br.com.wdc.shopping.domain.model.Purchase
import br.com.wdc.shopping.domain.model.PurchaseItem
import br.com.wdc.shopping.domain.model.User
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Gson compartilhado para os endpoints REST da API de repositório.
 *
 * Configurações:
 * - OffsetDateTime serializado como ISO-8601 string
 * - PurchaseItem.purchase ignorado para evitar referência circular
 * - Product.image ignorado (usar endpoint dedicado /api/repo/product/{id}/image)
 * - User.password somente escrita (não serializado)
 * - Campos null não são serializados
 */
object ApiGson {

    val instance: Gson = createGson()

    private fun createGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeSerializer())
            .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeDeserializer())
            .addSerializationExclusionStrategy(ApiExclusionStrategy())
            .create()
    }

    /**
     * Deserializa o campo "projection" do body JSON para o tipo de entidade informado.
     * Retorna null se o campo não estiver presente.
     */
    fun <T> parseProjection(body: JsonObject, type: Class<T>): T? {
        val projectionElement = body.get("projection")
        if (projectionElement != null && !projectionElement.isJsonNull) {
            return instance.fromJson(projectionElement, type)
        }
        return null
    }

    private class OffsetDateTimeSerializer : JsonSerializer<OffsetDateTime> {
        override fun serialize(src: OffsetDateTime, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
    }

    private class OffsetDateTimeDeserializer : JsonDeserializer<OffsetDateTime> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): OffsetDateTime {
            return OffsetDateTime.parse(json.asString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }
    }

    /**
     * Estratégia de exclusão que:
     * - Ignora PurchaseItem.purchase (referência circular)
     * - Ignora Product.image (usar endpoint dedicado)
     * - Ignora User.password na serialização
     */
    private class ApiExclusionStrategy : ExclusionStrategy {
        override fun shouldSkipField(f: FieldAttributes): Boolean {
            // PurchaseItem.purchase → evita referência circular
            if (f.declaringClass == PurchaseItem::class.java && f.name == "purchase") {
                return true
            }
            // Product.image → usar endpoint dedicado
            if (f.declaringClass == Product::class.java && f.name == "image") {
                return true
            }
            // User.password → nunca serializar
            if (f.declaringClass == User::class.java && f.name == "password") {
                return true
            }
            return false
        }

        override fun shouldSkipClass(clazz: Class<*>): Boolean = false
    }
}
