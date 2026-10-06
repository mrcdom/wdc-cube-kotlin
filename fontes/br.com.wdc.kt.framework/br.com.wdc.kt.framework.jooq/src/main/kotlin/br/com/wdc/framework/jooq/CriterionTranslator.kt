package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.Operator
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL

/**
 * Traduz um campo de critério em condição jOOQ — a **única** tradução critério → SQL.
 *
 * O que fica por entidade é só o que ela sabe e o framework não: qual coluna corresponde a cada campo e,
 * quando o tipo do domínio difere do da coluna, como converter o valor.
 *
 * **Campo não informado devolve `null`** e não entra na consulta; critério inteiramente vazio resulta em
 * `noCondition()` — nunca uma condição falsa, que transformaria "sem filtro" em "nenhum resultado".
 */
object CriterionTranslator {

    /** Condição de um campo cujo valor vai ao banco como está. */
    fun <V : Any> translate(column: Field<out V?>, criterion: Criterion<*, V>?): Condition? =
        translateWith(column, criterion, null)

    /**
     * Condição de um campo cujo valor precisa ser convertido antes de chegar à coluna. O conversor vale
     * igual para `eq`, `between` e `isIn`.
     *
     * @param A tipo do valor no domínio — `Instant`, `Double`
     * @param B tipo da coluna — `LocalDateTime`, `BigDecimal`
     */
    fun <A : Any, B : Any> translate(column: Field<out B?>, criterion: Criterion<*, A>?, converter: (A) -> B): Condition? =
        translateWith(column, criterion, converter)

    /**
     * Condição de um campo sobre a tabela informada, e não sobre a que declarou a coluna — serve à
     * subconsulta correlacionada, onde a instância da tabela tem alias próprio.
     */
    fun <V : Any> translate(table: Table<*>, column: TableField<*, out V?>, criterion: Criterion<*, V>?): Condition? {
        if (criterion == null || !criterion.isSet()) return null
        return translateWith(column(table, column), criterion, null)
    }

    /** Como a anterior, para campo que precisa de conversão. */
    fun <A : Any, B : Any> translate(
        table: Table<*>,
        column: TableField<*, out B?>,
        criterion: Criterion<*, A>?,
        converter: (A) -> B,
    ): Condition? {
        if (criterion == null || !criterion.isSet()) return null
        return translateWith(column(table, column), criterion, converter)
    }

    /**
     * A coluna, resolvida em [table].
     *
     * **Recusa quando não existe, em vez de ignorar o filtro.** Devolver `noCondition()` aí entregaria o
     * conjunto inteiro como se o filtro tivesse sido aplicado.
     */
    fun <V> column(table: Table<*>, column: TableField<*, V>): Field<V> =
        table.field(column) ?: throw IllegalArgumentException("a coluna ${column.name} não existe em ${table.name}")

    /** Junta as condições que sobraram; lista vazia (ou só de `null`) devolve `noCondition()`. */
    fun and(conditions: List<Condition?>): Condition {
        val useful = conditions.filterNotNull()
        return if (useful.isEmpty()) DSL.noCondition() else DSL.and(useful)
    }

    private fun <A : Any> translateWith(column: Field<*>, criterion: Criterion<*, A>?, converter: ((A) -> Any)?): Condition? {
        if (criterion == null || !criterion.isSet()) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val target = column as Field<Any?>
        val ofField = criterion.predicates.mapNotNull { condition(target, it, converter) }
        return when {
            ofField.isEmpty() -> null
            ofField.size == 1 -> ofField[0]
            // AND por padrão — é o que faz ge(inicio) com le(fim) significar intervalo, e ne(1) com ne(2) excluir
            // os dois. Só vira OR quando o campo foi marcado com or(), e a disjunção fica contida no campo.
            criterion.disjunctive -> DSL.or(ofField)
            else -> DSL.and(ofField)
        }
    }

    private fun <A : Any> condition(column: Field<Any?>, predicate: Criterion.Predicate<A>, converter: ((A) -> Any)?): Condition? {
        val op = predicate.operator
        if (op == Operator.IS_NULL) return column.isNull
        if (op == Operator.IS_NOT_NULL) return column.isNotNull

        val values: List<Any?> = predicate.values.map { v -> if (v == null || converter == null) v else converter(v) }
        if (!op.accepts(values.size)) {
            // operador e valores em desacordo — ignorar é melhor do que emitir SQL que o banco recusaria
            return null
        }
        return when (op) {
            Operator.EQ -> column.eq(values[0])
            Operator.NE -> column.ne(values[0])
            Operator.GT -> column.gt(values[0])
            Operator.GE -> column.ge(values[0])
            Operator.LT -> column.lt(values[0])
            Operator.LE -> column.le(values[0])
            // like/ilike só são oferecidos em TextCriterion, que quem declara o campo escolhe pelo tipo da coluna
            Operator.LIKE -> column.like(values[0] as String)
            Operator.ILIKE -> column.likeIgnoreCase(values[0] as String)
            Operator.BETWEEN -> column.between(values[0], values[1])
            Operator.IN -> column.`in`(values)
            Operator.IS_NULL, Operator.IS_NOT_NULL -> null
        }
    }
}
