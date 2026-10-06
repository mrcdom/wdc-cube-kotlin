package br.com.wdc.framework.domain.criteria

/**
 * Critério de campo ordenável — número, data, timestamp.
 *
 * Acrescenta à igualdade o que só faz sentido onde existe ordem. A separação em subclasse é o que impede
 * `criteria.roles.between(...)` de compilar: quem declara o campo conhece o tipo da coluna e escolhe a
 * classe, então a restrição não custa nada a quem escreve o critério e evita o erro na origem.
 *
 * @param C tipo do `XxxCriteria` que contém este campo
 * @param T tipo do valor comparado
 */
open class ComparableCriterion<C, T : Any>(owner: C?, name: String) : Criterion<C, T>(owner, name) {

    // Retorno covariante: sem isto, or() devolveria Criterion e o encadeamento perderia between e as comparações.
    override fun or(): ComparableCriterion<C, T> {
        super.or()
        return this
    }

    override fun and(): ComparableCriterion<C, T> {
        super.and()
        return this
    }

    /** Maior que. `null` não acrescenta pedido. */
    fun gt(value: T?): C = if (value == null) owner() else add(Operator.GT, value)

    /** Maior ou igual a. `null` não acrescenta pedido. */
    fun ge(value: T?): C = if (value == null) owner() else add(Operator.GE, value)

    /** Menor que. `null` não acrescenta pedido. */
    fun lt(value: T?): C = if (value == null) owner() else add(Operator.LT, value)

    /** Menor ou igual a. `null` não acrescenta pedido. */
    fun le(value: T?): C = if (value == null) owner() else add(Operator.LE, value)

    /**
     * Intervalo fechado nos dois extremos.
     *
     * **Aceita limite aberto.** Só o início vira `>=`; só o fim vira `<=`; nenhum dos dois não acrescenta
     * pedido. É o caso corrente de filtro por período em tela, onde costuma-se preencher uma data só.
     */
    fun between(start: T?, end: T?): C = when {
        start == null && end == null -> owner()
        start == null -> le(end)
        end == null -> ge(start)
        else -> add(Operator.BETWEEN, start, end)
    }
}
