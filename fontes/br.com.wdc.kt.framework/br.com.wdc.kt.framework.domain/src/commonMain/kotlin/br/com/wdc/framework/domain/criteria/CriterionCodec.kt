package br.com.wdc.framework.domain.criteria

import br.com.wdc.framework.commons.serialization.ExtensibleObjectInput
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.commons.serialization.InputCoerceUtils
import br.com.wdc.framework.commons.serialization.SerializationToken
import br.com.wdc.framework.domain.exception.InvalidRequestException
import kotlin.time.Instant

/** Como escrever um valor do tipo do campo. */
typealias CriterionValueWriter<T> = (out: ExtensibleObjectOutput, value: T) -> Unit

/** Como ler um valor do tipo do campo. */
typealias CriterionValueReader<T> = (input: ExtensibleObjectInput) -> T?

/**
 * Como um campo de critério trafega.
 *
 * Escrito uma vez, aqui, em vez de repetido em cada codec de entidade: o que muda de campo para campo é o
 * tipo do valor, e não a forma. Cada codec só diz qual leitor/escritor de valor usar.
 *
 * A forma é um objeto com os pedidos — **é contrato com os outros clientes; não altere**:
 * ```
 * "price": { "or": true, "p": [ { "o": "GE", "v": [10.0] }, { "o": "IS_NULL" } ] }
 * ```
 *
 * **O valor solto não bastaria.** Um campo carrega vários pedidos, cada um com seu operador e sua aridade,
 * e a disjunção é do campo, não do pedido. Reduzir isso a `"price": 10.0` descartaria tudo menos a
 * igualdade, e em silêncio: o outro lado receberia um filtro mais frouxo do que o pedido.
 */
object CriterionCodec {

    val LONG_OUT: CriterionValueWriter<Long> = { out, v -> out.value(v) }
    val LONG_IN: CriterionValueReader<Long> = { input -> InputCoerceUtils.asLong(input) }

    val INT_OUT: CriterionValueWriter<Int> = { out, v -> out.value(v.toLong()) }
    val INT_IN: CriterionValueReader<Int> = { input -> InputCoerceUtils.asInteger(input) }

    val DOUBLE_OUT: CriterionValueWriter<Double> = { out, v -> out.value(v) }
    val DOUBLE_IN: CriterionValueReader<Double> = { input -> InputCoerceUtils.asDouble(input) }

    val STRING_OUT: CriterionValueWriter<String> = { out, v -> out.value(v) }
    val STRING_IN: CriterionValueReader<String> = { input -> InputCoerceUtils.asString(input) }

    val BOOL_OUT: CriterionValueWriter<Boolean> = { out, v -> out.value(v) }
    val BOOL_IN: CriterionValueReader<Boolean> = { input -> InputCoerceUtils.asBoolean(input) }

    /**
     * Instante como texto ISO-8601. Escreve em UTC (`…Z`); lê também com offset (`…+03:00`), que é como
     * os clientes Java o enviam.
     */
    val INSTANT_OUT: CriterionValueWriter<Instant> = { out, v -> out.value(v.toString()) }
    val INSTANT_IN: CriterionValueReader<Instant> = { input -> InputCoerceUtils.asString(input)?.let(Instant::parse) }

    /**
     * Lê o nome da ordenação, recusando o que não reconhece.
     *
     * **Ao contrário do operador desconhecido, que é descartado**, aqui a leitura falha. A diferença é o que
     * cada descarte produziria: sem um pedido, o filtro fica mais frouxo e o campo continua listado — o
     * descompasso aparece; sem a ordenação, a lista volta numa ordem qualquer, e quem a exibe a apresenta
     * como se fosse a ordem pedida.
     *
     * Recebe os valores aceitos (`XxxCriteria.OrderBy.entries`) em vez da classe: sem reflexão.
     *
     * @throws InvalidRequestException se o nome não estiver entre os aceitos — a camada REST devolve 400,
     *         nomeando o valor recebido e os aceitos
     */
    fun <E : Enum<E>> readOrderBy(input: ExtensibleObjectInput, accepted: List<E>): E? {
        val name = InputCoerceUtils.asString(input) ?: return null
        return accepted.firstOrNull { it.name == name }
            ?: throw InvalidRequestException(
                "ordenação desconhecida: '$name' — aceitas: ${accepted.joinToString(", ") { it.name }}"
            )
    }

    /**
     * Escreve o campo, se houver o que escrever. Campo não informado não gera chave alguma — é o que
     * mantém curto o critério que filtra por um campo só, o caso corrente.
     */
    fun <T : Any> write(
        out: ExtensibleObjectOutput,
        name: String,
        criterion: Criterion<*, T>?,
        valueWriter: CriterionValueWriter<T>,
    ) {
        if (criterion == null || !criterion.isSet()) {
            return
        }
        out.name(name).beginObject()
        if (criterion.disjunctive) {
            out.name("or").value(true)
        }
        out.name("p").beginArray()
        for (predicate in criterion.predicates) {
            out.beginObject()
            out.name("o").value(predicate.operator.name)
            if (predicate.values.isNotEmpty()) {
                out.name("v").beginArray()
                for (value in predicate.values) {
                    if (value == null) out.nullValue() else valueWriter(out, value)
                }
                out.endArray()
            }
            out.endObject()
        }
        out.endArray()
        out.endObject()
    }

    /**
     * Lê o campo no formato acima, repondo os pedidos no [criterion].
     *
     * Repõe por [Criterion.restore], e não pelos métodos fluentes: `eq(null)` não acrescenta pedido — o certo
     * para quem monta filtro de tela — e apagaria em silêncio o que o outro lado enviou.
     *
     * **Aceita também o valor solto** (`"price": 10.0`), lendo-o como igualdade — a forma mínima de um
     * filtro de igualdade, e a que os clientes anteriores a este formato enviam. `null` solto é descartado.
     */
    fun <T : Any> read(input: ExtensibleObjectInput, criterion: Criterion<*, T>, valueReader: CriterionValueReader<T>) {
        if (input.peek() != SerializationToken.BEGIN_OBJECT) {
            val value = valueReader(input)
            if (value != null) {
                criterion.restore(Operator.EQ, listOf(value))
            }
            return
        }
        input.beginObject()
        while (input.hasNext()) {
            when (input.nextName()) {
                "or" -> criterion.restoreDisjunctive(InputCoerceUtils.asBoolean(input) == true)
                "p" -> readPredicates(input, criterion, valueReader)
                else -> input.skipValue()
            }
        }
        input.endObject()
    }

    private fun <T : Any> readPredicates(
        input: ExtensibleObjectInput,
        criterion: Criterion<*, T>,
        valueReader: CriterionValueReader<T>,
    ) {
        input.beginArray()
        while (input.hasNext()) {
            var operator: Operator? = null
            val values = ArrayList<T?>(2)
            input.beginObject()
            while (input.hasNext()) {
                when (input.nextName()) {
                    "o" -> operator = parseOperator(InputCoerceUtils.asString(input))
                    "v" -> {
                        input.beginArray()
                        while (input.hasNext()) {
                            values.add(
                                if (input.peek() == SerializationToken.NULL) input.nextNull() else valueReader(input)
                            )
                        }
                        input.endArray()
                    }
                    else -> input.skipValue()
                }
            }
            input.endObject()
            if (operator != null) {
                criterion.restore(operator, values)
            }
        }
        input.endArray()
    }

    /**
     * Operador desconhecido é descartado, e não traduzido por aproximação.
     *
     * Acontece quando um lado é mais novo que o outro e envia um operador que este ainda não conhece.
     * Escolher o "mais parecido" produziria um filtro diferente do pedido, sem nada assinalar; deixar o
     * pedido de fora produz um filtro mais frouxo — mas o campo continua listado, e o descompasso aparece.
     */
    private fun parseOperator(name: String?): Operator? =
        if (name == null) null else Operator.entries.firstOrNull { it.name == name }
}
