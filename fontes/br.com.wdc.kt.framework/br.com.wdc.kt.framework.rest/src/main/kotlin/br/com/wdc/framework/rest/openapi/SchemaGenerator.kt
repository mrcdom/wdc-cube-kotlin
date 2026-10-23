package br.com.wdc.framework.rest.openapi

import com.google.gson.FieldNamingStrategy
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Date
import java.util.UUID

/**
 * Esquema OpenAPI de um tipo, lido por reflexão dos campos — os mesmos que o Gson serializa, com os mesmos
 * nomes. Classe de DTO vira componente (`$ref`), registrado uma vez com o nome simples da classe.
 *
 * Tipos genéricos (`Page<UserDto>`) viram componentes nomeados pelos argumentos (`PageUserDto`).
 */
class SchemaGenerator(private val naming: FieldNamingStrategy) {

    /** Os componentes gerados até aqui, por nome. */
    val components = LinkedHashMap<String, Any?>()

    fun schemaOf(type: Type): Map<String, Any?> = schemaOf(type, emptyMap())

    private fun schemaOf(type: Type, bindings: Map<String, Type>): Map<String, Any?> = when (type) {
        is Class<*> -> classSchema(type, emptyList())
        is ParameterizedType -> {
            val raw = type.rawType as Class<*>
            val args = type.actualTypeArguments.map { resolve(it, bindings) }
            when {
                Collection::class.java.isAssignableFrom(raw) || Iterable::class.java.isAssignableFrom(raw) ->
                    mapOf("type" to "array", "items" to schemaOf(args.first(), bindings))
                Map::class.java.isAssignableFrom(raw) ->
                    mapOf("type" to "object", "additionalProperties" to schemaOf(args.getOrElse(1) { Any::class.java }, bindings))
                else -> classSchema(raw, args)
            }
        }
        is GenericArrayType -> mapOf("type" to "array", "items" to schemaOf(type.genericComponentType, bindings))
        is WildcardType -> schemaOf(type.upperBounds.first(), bindings)
        is TypeVariable<*> -> bindings[type.name]?.let { schemaOf(it, bindings) } ?: emptyMap()
        else -> emptyMap()
    }

    private fun resolve(type: Type, bindings: Map<String, Type>): Type =
        if (type is TypeVariable<*>) bindings[type.name] ?: Any::class.java else if (type is WildcardType) type.upperBounds.first() else type

    private fun classSchema(type: Class<*>, args: List<Type>): Map<String, Any?> {
        scalar(type)?.let { return it }
        if (type.isArray) return mapOf("type" to "array", "items" to schemaOf(type.componentType))
        if (type.isEnum) return mapOf("type" to "string", "enum" to type.enumConstants.map { enumName(it as Enum<*>) })
        if (Collection::class.java.isAssignableFrom(type)) return mapOf("type" to "array", "items" to emptyMap<String, Any?>())
        if (Map::class.java.isAssignableFrom(type)) return mapOf("type" to "object")
        if (type == Any::class.java) return emptyMap()

        val name = type.simpleName + args.joinToString("") { simpleName(it) }
        if (name !in components) {
            components[name] = null // reserva: tipos recursivos param aqui
            val bindings = type.typeParameters.map { it.name }.zip(args).toMap()
            val properties = LinkedHashMap<String, Any?>()
            for (field in fieldsOf(type)) {
                properties[jsonName(field)] = schemaOf(field.genericType, bindings)
            }
            components[name] = mapOf("type" to "object", "properties" to properties)
        }
        return mapOf("\$ref" to "#/components/schemas/$name")
    }

    private fun simpleName(type: Type): String = when (type) {
        is Class<*> -> type.simpleName
        is ParameterizedType -> (type.rawType as Class<*>).simpleName + type.actualTypeArguments.joinToString("") { simpleName(it) }
        else -> "Any"
    }

    private fun enumName(constant: Enum<*>): String =
        constant.javaClass.getField(constant.name).getAnnotation(SerializedName::class.java)?.value ?: constant.name

    private fun fieldsOf(type: Class<*>): List<Field> {
        val chain = ArrayList<Class<*>>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            chain.add(0, current)
            current = current.superclass
        }
        return chain.flatMap { c -> c.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && !it.isSynthetic } }
    }

    private fun jsonName(field: Field): String = field.getAnnotation(SerializedName::class.java)?.value ?: naming.translateName(field)

    private fun scalar(type: Class<*>): Map<String, Any?>? = when (type) {
        String::class.java, Char::class.javaObjectType, Char::class.javaPrimitiveType -> mapOf("type" to "string")
        Boolean::class.javaObjectType, Boolean::class.javaPrimitiveType -> mapOf("type" to "boolean")
        Int::class.javaObjectType, Int::class.javaPrimitiveType, Short::class.javaObjectType, Short::class.javaPrimitiveType,
        Byte::class.javaObjectType, Byte::class.javaPrimitiveType -> mapOf("type" to "integer", "format" to "int32")
        Long::class.javaObjectType, Long::class.javaPrimitiveType, BigInteger::class.java -> mapOf("type" to "integer", "format" to "int64")
        Double::class.javaObjectType, Double::class.javaPrimitiveType -> mapOf("type" to "number", "format" to "double")
        Float::class.javaObjectType, Float::class.javaPrimitiveType -> mapOf("type" to "number", "format" to "float")
        BigDecimal::class.java -> mapOf("type" to "number")
        UUID::class.java -> mapOf("type" to "string", "format" to "uuid")
        LocalDate::class.java -> mapOf("type" to "string", "format" to "date")
        LocalDateTime::class.java, Instant::class.java, OffsetDateTime::class.java, ZonedDateTime::class.java, Date::class.java ->
            mapOf("type" to "string", "format" to "date-time")
        LocalTime::class.java -> mapOf("type" to "string", "format" to "time")
        ByteArray::class.java -> mapOf("type" to "string", "format" to "byte")
        else -> if (Number::class.java.isAssignableFrom(type)) mapOf("type" to "number") else null
    }
}
