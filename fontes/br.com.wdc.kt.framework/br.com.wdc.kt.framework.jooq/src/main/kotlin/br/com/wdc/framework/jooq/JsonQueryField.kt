package br.com.wdc.framework.jooq

import org.jooq.Field
import org.jooq.Table
import org.jooq.TableField

/**
 * Um campo do mapeamento, como o [JsonQueryBuilder] o conhece: o nome, a coluna, como ler e gravar no bean. É o que
 * o [JsonQuery.insert] usa para montar o `INSERT` sem que o repositório repita o mapeamento.
 *
 * As associações declaradas com chave (`addBeanField(…, key = …)`) entram uma vez por parte da chave, com o nome
 * `associação.parte` (`author.id`) e a coluna da chave estrangeira **desta** tabela. Coleções não entram.
 *
 * @param B tipo do bean
 * @param T tipo da tabela jOOQ
 */
class JsonQueryField<B : Any, T : Table<*>> internal constructor(
    /** O nome no JSON; numa parte de chave de associação, `associação.parte`. */
    val name: String,
    /** A coluna, ou a expressão, sobre a instância da tabela. */
    val column: (T) -> Field<*>,
    /** O valor no bean, como o domínio o guarda (numa associação, a parte da chave do associado). */
    val get: (B) -> Any?,
    /** Grava o valor no bean; `null` não grava. Numa associação ausente, cria o associado só com a chave. */
    val set: (B, Any?) -> Unit,
    /** Se o bean de projeção pede este campo. */
    val projected: (B) -> Boolean,
    /** Marca o campo num bean de projeção. */
    internal val mark: (B) -> Unit,
    /** O valor do domínio no tipo da coluna (`Instant` → `LocalDateTime` em UTC, enum → nome…). */
    internal val toColumn: (Any) -> Any?,
) {
    /** `true` quando [column] é coluna de [table], e não uma expressão sobre ela (campo derivado). */
    fun isColumnOf(table: T): Boolean {
        val field = column(table)
        return field is TableField<*, *> && table.fields().any { it == field }
    }

    override fun toString(): String = "JsonQueryField($name)"
}
