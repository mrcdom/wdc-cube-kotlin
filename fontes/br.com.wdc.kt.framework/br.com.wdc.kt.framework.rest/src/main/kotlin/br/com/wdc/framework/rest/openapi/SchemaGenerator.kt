package br.com.wdc.framework.rest.openapi

import br.com.wdc.framework.domain.doc.ApiDoc
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
 * OpenAPI schema of a type, read by reflection from the fields — the same ones Gson serializes, with the same
 * names. A DTO class becomes a component (`$ref`), registered once with the class's simple name.
 *
 * Generic types (`Page<UserDto>`) become components named by their arguments (`PageUserDto`).
 *
 * Two different types with the same simple name (the `Request` and `Response` nested in each operation, for
 * example) don't overwrite each other: both take the name of their enclosing classes (`CreateOrder.Response` becomes
 * `CreateOrderAction.Response`), and, if they still clash, the package. The name depends only on the document's set
 * of types, not on the order they were seen; that is why references only get their name in [components].
 */
class SchemaGenerator(private val naming: FieldNamingStrategy) {

    private class Entry(val type: Class<*>, val args: List<Type>) {
        var schema: Map<String, Any?>? = null
        val refs = ArrayList<MutableMap<String, Any?>>()
    }

    private val entries = LinkedHashMap<List<Any>, Entry>()

    /** The components, by name; the references handed out by [schemaOf] get their name here. */
    val components: Map<String, Any?>
        get() {
            val names = componentNames()
            val out = LinkedHashMap<String, Any?>()
            for ((key, entry) in entries) {
                val name = names.getValue(key)
                entry.refs.forEach { it["\$ref"] = "#/components/schemas/$name" }
                out[name] = entry.schema
            }
            return out
        }

    private fun componentNames(): Map<List<Any>, String> {
        val result = HashMap<List<Any>, String>()
        val levels = listOf<(Class<*>) -> String>({ it.simpleName }, ::nestedName, { it.name })
        var pending = entries.keys.toList()
        for ((i, level) in levels.withIndex()) {
            val byName = pending.groupBy { key -> entries.getValue(key).let { level(it.type) + it.args.joinToString("") { a -> argName(a, level) } } }
            pending = emptyList()
            for ((name, keys) in byName) {
                if (keys.size == 1 || i == levels.lastIndex) keys.forEach { result[it] = name } else pending = pending + keys
            }
        }
        return result
    }

    /** The name with its enclosing classes: `CreateOrderAction.Response`. */
    private fun nestedName(type: Class<*>): String = generateSequence(type) { it.enclosingClass }.toList().reversed().joinToString(".") { it.simpleName }

    private fun argName(type: Type, level: (Class<*>) -> String): String = when (type) {
        is Class<*> -> level(type)
        is ParameterizedType -> level(type.rawType as Class<*>) + type.actualTypeArguments.joinToString("") { argName(it, level) }
        else -> "Any"
    }

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

        val key = listOf<Any>(type) + args
        val entry = entries[key] ?: Entry(type, args).also { created ->
            entries[key] = created // placeholder: recursive types stop here
            val bindings = type.typeParameters.map { it.name }.zip(args).toMap()
            val properties = LinkedHashMap<String, Any?>()
            for (field in fieldsOf(type)) {
                properties[jsonName(field)] = documented(schemaOf(field.genericType, bindings), field.getAnnotation(ApiDoc::class.java))
            }
            created.schema = linkedMapOf<String, Any?>("type" to "object").apply {
                type.getAnnotation(ApiDoc::class.java)?.let { put("description", it.value) }
                put("properties", properties)
            }
        }
        // The name is only decided in [components]: until then, the reference waits for it.
        return linkedMapOf<String, Any?>("\$ref" to null).also { entry.refs += it }
    }

    /**
     * The property with the field's `@ApiDoc`. A reference to another schema goes in an `allOf`: in OpenAPI 3.0,
     * whatever sits next to a `$ref` is ignored.
     */
    private fun documented(schema: Map<String, Any?>, doc: ApiDoc?): Map<String, Any?> {
        if (doc == null) return schema
        val base = if (schema.containsKey("\$ref")) linkedMapOf<String, Any?>("allOf" to listOf(schema)) else LinkedHashMap(schema)
        base["description"] = doc.value
        if (doc.example.isNotEmpty()) base["example"] = typedExample(doc.example, schema["type"])
        return base
    }

    /** The example in the schema's type: `4021` is a number in an integer field, not the text "4021". */
    private fun typedExample(example: String, type: Any?): Any = when (type) {
        "integer" -> example.toLongOrNull() ?: example
        "number" -> example.toBigDecimalOrNull() ?: example
        "boolean" -> example.toBooleanStrictOrNull() ?: example
        else -> example
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
