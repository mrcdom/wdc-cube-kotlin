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
 * Lê as classes Kotlin pelo **construtor primário**, em vez de deixar o Gson criar o objeto sem construtor e
 * gravar os campos por reflexão.
 *
 * Dois motivos:
 * - **campo `val` é final**, e o JDK 26 avisa que vai bloquear a gravação de campo final por reflexão (JEP 500):
 *   o leitor padrão do Gson deixaria de funcionar;
 * - pelo construtor, **os valores padrão dos parâmetros valem** quando o campo não vem no JSON.
 *
 * Campo ausente: parâmetro com padrão usa o padrão; anulável sem padrão fica `null`; não anulável sem padrão é
 * erro. `null` explícito: num anulável é `null`; num não anulável usa o padrão, se houver, e senão é erro. Os erros são
 * [JsonParseException] (400 na leitura do corpo).
 *
 * O nome no JSON é o do campo que guarda o parâmetro (`@SerializedName`, com os nomes alternativos, ou a
 * política de nomes); `@JsonAdapter` no campo vale (adaptador ou fábrica). Propriedades `var` declaradas fora do
 * construtor são gravadas depois de construir; `val` fora do construtor é ignorada na leitura. A escrita não
 * muda: segue o Gson.
 *
 * Fora daqui, e lidas pelo Gson como antes: enum, `object`, classe de valor, registro (`@JvmRecord`), classe
 * abstrata, classe sem parâmetros no construtor primário e classes que não são Kotlin.
 */
internal class KotlinConstructorFactory(private val naming: FieldNamingStrategy) : TypeAdapterFactory {

    private val plans = ConcurrentHashMap<Class<*>, Any>()

    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        if (plans.computeIfAbsent(raw) { kotlinConstructorOf(it) ?: NONE } === NONE) {
            return null
        }
        val delegate = gson.getDelegateAdapter(this, type)
        val reader = Reader(gson, type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)

            @Suppress("UNCHECKED_CAST")
            override fun read(input: JsonReader): T? = reader.read(input) as T?
        }
    }

    /** O construtor primário, se a classe é das que este leitor trata; `null` senão. */
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
            null // classes sintéticas, lambdas, classes locais que o kotlin-reflect não descreve
        } catch (_: Error) {
            null // KotlinReflectionInternalError: metadados que o kotlin-reflect não lê
        }
    }

    private inner class Reader(private val gson: Gson, type: TypeToken<*>) {

        private inner class Slot(val parameter: KParameter, val names: List<String>, val adapter: TypeAdapter<*>)

        private inner class Extra(val field: Field, val adapter: TypeAdapter<*>)

        private val constructor = plans[type.rawType] as KFunction<*>
        private val elements = gson.getAdapter(JsonElement::class.java)
        private val bindings = bindingsOf(type)
        private val slots: List<Slot>
        private val byName = HashMap<String, Slot>()
        private val extras = HashMap<String, Extra>()

        init {
            val fields = fieldsOf(type.rawType)
            slots = constructor.parameters.map { parameter ->
                val field = fields.firstOrNull { it.name == parameter.name }
                val names = if (field != null) jsonNames(field) else listOfNotNull(parameter.name)
                val adapter = field?.let { adapterOf(it) } ?: gson.getAdapter(TypeToken.get(resolve(parameter.type.javaType, bindings)))
                Slot(parameter, names, adapter).also { slot -> names.forEach { byName.putIfAbsent(it, slot) } }
            }
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
                throw JsonParseException("Esperava um objeto JSON para ${constructor.returnType}, veio $tree")
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
            for (slot in slots) {
                val parameter = slot.parameter
                val value = values[parameter]
                val nullable = parameter.type.isMarkedNullable
                when {
                    value != null -> arguments[parameter] = value
                    parameter in present && nullable -> arguments[parameter] = null // null explícito
                    parameter.isOptional -> Unit // vale o padrão
                    nullable -> arguments[parameter] = null
                    parameter in present -> throw JsonParseException("O campo '${slot.names.first()}' não pode ser nulo")
                    else -> throw JsonParseException("Falta o campo obrigatório '${slot.names.first()}'")
                }
            }

            val instance = try {
                constructor.callBy(arguments)
            } catch (e: InvocationTargetException) {
                throw e.targetException // validação no init: a exceção da classe, inteira
            }
            for ((extra, element) in later) {
                extra.field.set(instance, extra.adapter.fromJsonTree(element))
            }
            return instance
        }

        /** O adaptador de `@JsonAdapter` no campo, ou `null` se não há. */
        private fun adapterOf(field: Field): TypeAdapter<*>? {
            val annotation = field.getAnnotation(JsonAdapter::class.java) ?: return null
            val ctor = annotation.value.java.getDeclaredConstructor().also { it.isAccessible = true }
            val adapter: TypeAdapter<*> = when (val created = ctor.newInstance()) {
                is TypeAdapter<*> -> created
                is TypeAdapterFactory -> created.create(gson, TypeToken.get(resolve(field.genericType, bindings)))
                    ?: throw IllegalArgumentException("@JsonAdapter de ${field.name}: a fábrica não trata ${field.genericType}")
                else -> throw IllegalArgumentException(
                    "@JsonAdapter de ${field.name}: só TypeAdapter e TypeAdapterFactory são aceitos na leitura pelo construtor"
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

    private fun bindingsOf(type: TypeToken<*>): Map<String, Type> {
        val parameterized = type.type as? ParameterizedType ?: return emptyMap()
        return type.rawType.typeParameters.map { it.name }.zip(parameterized.actualTypeArguments).toMap()
    }

    /** Troca as variáveis de tipo da classe pelos argumentos pedidos (`Page<T>` lido como `Page<Order>`). */
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
