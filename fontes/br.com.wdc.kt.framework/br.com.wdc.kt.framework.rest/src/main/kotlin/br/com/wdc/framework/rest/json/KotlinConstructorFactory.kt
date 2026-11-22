package br.com.wdc.framework.rest.json

import com.google.gson.FieldNamingStrategy
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

/**
 * Reads Kotlin classes through the **primary constructor**, instead of letting Gson create the object without a
 * constructor and write the fields by reflection.
 *
 * Two reasons:
 * - **a `val` field is final**, and JDK 26 warns it will block writing final fields by reflection (JEP 500):
 *   Gson's default reader would stop working;
 * - through the constructor, **parameter default values apply** when the field is not in the JSON.
 *
 * Absent field: a parameter with a default uses the default; a nullable without a default becomes `null`; a
 * non-nullable without a default is an error. Explicit `null`: on a nullable it is `null`; on a non-nullable it uses
 * the default, if any, and otherwise is an error. The errors are [JsonParseException] (400 when reading the body).
 *
 * The JSON name is that of the field holding the parameter (`@SerializedName`, with the alternate names, or the
 * naming policy); `@JsonAdapter` on the field applies (adapter or factory). `var` properties declared outside the
 * constructor are written after construction; a `val` outside the constructor is ignored on reading. Writing
 * doesn't change: it follows Gson.
 *
 * A parameter whose field is **transient** (Kotlin's `@Transient` or the `transient` modifier) is not read from the
 * JSON, as in Gson: its key, if present, is ignored, and the parameter gets the default, or `null`. Without a default
 * and not nullable it is a declaration error: the class's first read fails with [JsonDeclarationException], which is
 * not 400.
 *
 * Left out, and read by Gson as before: enums, `object`s, value classes, records (`@JvmRecord`), abstract
 * classes, classes without parameters in the primary constructor and non-Kotlin classes.
 */
internal class KotlinConstructorFactory(private val naming: FieldNamingStrategy) : TypeAdapterFactory {

    private val plans = ConcurrentHashMap<Class<*>, Any>()

    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        if (plans.computeIfAbsent(raw) { kotlinConstructorOf(it) ?: NONE } === NONE) {
            return null
        }
        val delegate = gson.getDelegateAdapter(this, type)
        // built on the first read: a declaration error doesn't prevent writing
        val reader by lazy { Reader(gson, type) }
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)

            @Suppress("UNCHECKED_CAST")
            override fun read(input: JsonReader): T? = reader.read(input) as T?
        }
    }

    /** The primary constructor, if the class is one this reader handles; `null` otherwise. */
    private fun kotlinConstructorOf(raw: Class<*>): KFunction<*>? {
        if (!raw.isAnnotationPresent(Metadata::class.java) || raw.isEnum || raw.isInterface || raw.isRecord ||
            raw.isArray || Modifier.isAbstract(raw.modifiers) || raw.name.startsWith("kotlin.")
        ) {
            return null
        }
        return try {
            val kClass: KClass<*> = raw.kotlin
            if (kClass.objectInstance != null || kClass.isValue || kClass.isInner) return null
            kClass.primaryConstructor?.takeIf { it.parameters.isNotEmpty() }?.also { it.isAccessible = true }
        } catch (_: UnsupportedOperationException) {
            null // synthetic classes, lambdas, local classes that kotlin-reflect doesn't describe
        } catch (_: Error) {
            null // KotlinReflectionInternalError: metadata that kotlin-reflect can't read
        }
    }

    private inner class Reader(private val gson: Gson, type: TypeToken<*>) {

        private inner class Slot(val parameter: KParameter, val names: List<String>, val adapter: TypeAdapter<*>)

        private inner class Extra(val field: Field, val adapter: TypeAdapter<*>)

        private val constructor = plans[type.rawType] as KFunction<*>
        private val elements = gson.getAdapter(JsonElement::class.java)
        private val bindings = bindingsOf(type)
        private val slots: List<Slot>
        private val transientNulls: List<KParameter> // nullable transients without a default: get `null`
        private val byName = HashMap<String, Slot>()
        private val extras = HashMap<String, Extra>()

        init {
            val fields = fieldsOf(type.rawType)
            val transient = transientNamesOf(type.rawType)
            slots = constructor.parameters.filter { parameter ->
                if (parameter.name !in transient) return@filter true
                if (!parameter.isOptional && !parameter.type.isMarkedNullable) {
                    throw JsonDeclarationException(
                        "${type.rawType.name}: parameter '${parameter.name}' is transient (not read from JSON) and needs " +
                            "a default value or to be nullable"
                    )
                }
                false
            }.map { parameter ->
                val field = fields.firstOrNull { it.name == parameter.name }
                val names = if (field != null) jsonNames(field) else listOfNotNull(parameter.name)
                val adapter = field?.let { adapterOf(it) } ?: gson.getAdapter(TypeToken.get(resolve(parameter.type.javaType, bindings)))
                Slot(parameter, names, adapter).also { slot -> names.forEach { byName.putIfAbsent(it, slot) } }
            }
            transientNulls = constructor.parameters.filter { it.name in transient && !it.isOptional }
            val inConstructor = constructor.parameters.mapNotNull { it.name }.toSet()
            for (field in fields) {
                if (field.name in inConstructor || Modifier.isFinal(field.modifiers)) continue
                if (!field.trySetAccessible()) continue
                val adapter = adapterOf(field) ?: gson.getAdapter(TypeToken.get(resolve(field.genericType, bindings)))
                jsonNames(field).forEach { extras.putIfAbsent(it, Extra(field, adapter)) }
            }
        }

        fun read(input: JsonReader): Any? {
            if (input.peek() == JsonToken.NULL) {
                input.nextNull()
                return null
            }
            val tree = elements.read(input)
            if (tree !is JsonObject) {
                throw JsonParseException("Expected a JSON object for ${constructor.returnType}, got $tree")
            }
            val values = HashMap<KParameter, Any?>()
            val present = HashSet<KParameter>()
            val later = ArrayList<Pair<Extra, JsonElement>>()
            for ((name, element) in tree.entrySet()) {
                val slot = byName[name]
                if (slot != null) {
                    if (present.add(slot.parameter)) values[slot.parameter] = slot.adapter.fromJsonTree(element)
                    continue
                }
                extras[name]?.let { later += it to element }
            }

            val arguments = HashMap<KParameter, Any?>()
            transientNulls.forEach { arguments[it] = null }
            for (slot in slots) {
                val parameter = slot.parameter
                val value = values[parameter]
                val nullable = parameter.type.isMarkedNullable
                when {
                    value != null -> arguments[parameter] = value
                    parameter in present && nullable -> arguments[parameter] = null // explicit null
                    parameter.isOptional -> Unit // the default applies
                    nullable -> arguments[parameter] = null
                    parameter in present -> throw JsonParseException("Field '${slot.names.first()}' must not be null")
                    else -> throw JsonParseException("Missing required field '${slot.names.first()}'")
                }
            }

            val instance = try {
                constructor.callBy(arguments)
            } catch (e: InvocationTargetException) {
                throw e.targetException // validation in init: the class's own exception, untouched
            }
            for ((extra, element) in later) {
                extra.field.set(instance, extra.adapter.fromJsonTree(element))
            }
            return instance
        }

        /** The adapter from `@JsonAdapter` on the field, or `null` if there is none. */
        private fun adapterOf(field: Field): TypeAdapter<*>? {
            val annotation = field.getAnnotation(JsonAdapter::class.java) ?: return null
            val ctor = annotation.value.java.getDeclaredConstructor().also { it.isAccessible = true }
            val adapter: TypeAdapter<*> = when (val created = ctor.newInstance()) {
                is TypeAdapter<*> -> created
                is TypeAdapterFactory -> created.create(gson, TypeToken.get(resolve(field.genericType, bindings)))
                    ?: throw IllegalArgumentException("@JsonAdapter of ${field.name}: the factory doesn't handle ${field.genericType}")
                else -> throw IllegalArgumentException(
                    "@JsonAdapter of ${field.name}: only TypeAdapter and TypeAdapterFactory are accepted when reading through the constructor"
                )
            }
            return if (annotation.nullSafe) adapter.nullSafe() else adapter
        }
    }

    private fun jsonNames(field: Field): List<String> {
        val annotation = field.getAnnotation(SerializedName::class.java) ?: return listOf(naming.translateName(field))
        return listOf(annotation.value) + annotation.alternate
    }

    private fun fieldsOf(type: Class<*>): List<Field> {
        val fields = ArrayList<Field>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            current.declaredFields.filterTo(fields) { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && !it.isSynthetic }
            current = current.superclass
        }
        return fields
    }

    /** Names of the transient fields of the class and its superclasses. */
    private fun transientNamesOf(type: Class<*>): Set<String> {
        val names = HashSet<String>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            current.declaredFields.filterTo(ArrayList()) { !Modifier.isStatic(it.modifiers) && Modifier.isTransient(it.modifiers) }
                .mapTo(names) { it.name }
            current = current.superclass
        }
        return names
    }

    private fun bindingsOf(type: TypeToken<*>): Map<String, Type> {
        val parameterized = type.type as? ParameterizedType ?: return emptyMap()
        return type.rawType.typeParameters.map { it.name }.zip(parameterized.actualTypeArguments).toMap()
    }

    /** Replaces the class's type variables with the requested arguments (`Page<T>` read as `Page<Order>`). */
    private fun resolve(type: Type, bindings: Map<String, Type>): Type = when {
        bindings.isEmpty() -> type
        type is TypeVariable<*> -> bindings[type.name] ?: type
        type is ParameterizedType -> {
            val args = type.actualTypeArguments.map { resolve(it, bindings) }.toTypedArray()
            TypeToken.getParameterized(type.rawType, *args).type
        }
        type is GenericArrayType -> TypeToken.getArray(resolve(type.genericComponentType, bindings)).type
        type is WildcardType -> resolve(type.upperBounds.first(), bindings)
        else -> type
    }

    private companion object {
        val NONE = Any()
    }
}

/**
 * Class badly declared for JSON reading (for example, a transient parameter without a default and not nullable). It is
 * a code defect, not a request one: it doesn't become 400.
 */
class JsonDeclarationException(message: String) : RuntimeException(message)
